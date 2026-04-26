package com.zeromail.api.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zeromail.api.config.ZeroMailApiProperties;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.onboarding.model.OnboardingStep;
import com.zeromail.core.tenant.TenantContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Leg-2 OAuth success handler bound to client registration {@code google-gmail}
 * (D-A1). Mirrors {@link GoogleOAuthSuccessHandler}'s thin-adapter shape but adds
 * the same-Google-subject integrity check BEFORE any persistence.
 *
 * <p>One handler, one transaction, one invariant (CONTEXT §Specifics):
 * <ol>
 *   <li>Load the {@link OAuth2AuthorizedClient} once at the top of the callback so
 *       both the success path and any {@link GmailIdentityMismatchException} throw
 *       site reuse the captured token (avoids double-loads when the failure handler
 *       still needs the access-token to revoke).</li>
 *   <li>Resolve the currently signed-in user by leg-2 OIDC subject. If the lookup
 *       fails or the stored {@code googleSubject} differs from
 *       {@link OidcUser#getSubject()}, throw the typed mismatch exception — Spring
 *       Security routes it through {@code GmailOAuthFailureHandler} which revokes
 *       the captured token and redirects to {@code /onboarding?error=gmail_identity_mismatch}.</li>
 *   <li>On match, filter the leg-2 access-token scopes down to gmail-prefix only
 *       (Pitfall 5: {@code include_granted_scopes=true} returns leg-1 scopes too —
 *       openid+profile+email are leg-1 surface and not part of the Gmail
 *       connection's audit trail), encrypt the refresh token via the shared
 *       AES-GCM envelope, upsert the {@code GmailConnectionEntity} row, and
 *       advance {@code OnboardingStep} {@code SIGNED_IN → GMAIL_CONNECTED}.</li>
 * </ol>
 *
 * <p><b>Pitfall 6 / FND-05 invariant:</b> the {@link TenantContext#TENANT}
 * {@link ScopedValue} is bound BEFORE opening the JPA transaction so Hibernate
 * captures the right tenant on session open. Mirror order is the same as
 * {@code OAuthProvisioningService.createTenantAndUser} (lines 78-86).
 *
 * <p><b>Privacy contract (D-E1, threat T-1.4-02-token-leak):</b> log statements
 * emit only opaque event names + tenantId UUID — never email, Google subject,
 * or token contents.
 */
@Component
public class GmailOAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GmailOAuthSuccessHandler.class);
    private static final String GMAIL_REGISTRATION_ID = "google-gmail";
    private static final String GMAIL_SCOPE_PREFIX = "https://www.googleapis.com/auth/gmail.";

    private final UserRepository users;
    private final GmailConnectionService connections;
    private final RefreshTokenCipher cipher;
    private final OAuth2AuthorizedClientService authorizedClients;
    private final TransactionTemplate tx;

    public GmailOAuthSuccessHandler(
            UserRepository users,
            GmailConnectionService connections,
            RefreshTokenCipher cipher,
            OAuth2AuthorizedClientService authorizedClients,
            PlatformTransactionManager txManager,
            ZeroMailApiProperties properties) {
        this.users = users;
        this.connections = connections;
        this.cipher = cipher;
        this.authorizedClients = authorizedClients;
        this.tx = new TransactionTemplate(txManager);
        setDefaultTargetUrl(stripTrailingSlash(properties.web().baseUrl().toString()) + "/onboarding");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) auth;
        OidcUser leg2 = (OidcUser) token.getPrincipal();

        // 0. Load the AuthorizedClient ONCE — both the success path and any
        //    GmailIdentityMismatchException throw site reuse this single load.
        //    Avoids double-invocation of OAuth2AuthorizedClientService when the
        //    user-not-found branch and the subject-mismatch branch each construct
        //    the exception (one logical OAuth callback => one AuthorizedClient lookup).
        OAuth2AuthorizedClient authorizedClient = authorizedClients.loadAuthorizedClient(
                GMAIL_REGISTRATION_ID, token.getName());

        // Capture the just-issued access token NOW so the failure handler can
        // revoke it later — Pitfall 2 says the failure handler removes the
        // AuthorizedClient before redirecting, so re-loading inside the exception
        // would get null. Empty string when client / token is null; revoker treats
        // empty as a no-op.
        String accessTokenForRevoke = (authorizedClient != null && authorizedClient.getAccessToken() != null)
                ? authorizedClient.getAccessToken().getTokenValue()
                : "";

        // 1. Resolve the currently-bound user by leg-2 OIDC subject.
        //    Per CONTEXT D-A1: leg-2 user MUST already exist (signed in via leg 1). If not,
        //    that's an integrity error — fail loudly via the failure handler.
        UserEntity currentUser = users.findByGoogleSubject(leg2.getSubject())
                .orElseThrow(() -> new GmailIdentityMismatchException(accessTokenForRevoke));

        // 2. Subject check. (Tautological in the lookup form above, but kept explicit so the
        //    contract is visible if we later switch to a session-principal-based current-user
        //    lookup.) Reuse the captured token (no re-load).
        if (!leg2.getSubject().equals(currentUser.getGoogleSubject())) {
            throw new GmailIdentityMismatchException(accessTokenForRevoke);
        }

        // 3. Extract refresh token + scopes from the AuthorizedClient loaded in step 0.
        //    (No second loadAuthorizedClient call — single source of truth for this callback.)
        String refreshToken = authorizedClient.getRefreshToken().getTokenValue();
        Set<String> scopes = authorizedClient.getAccessToken().getScopes();

        // Filter to gmail-prefixed scopes only (Pitfall 5: include_granted_scopes=true returns
        // ALL leg-1+leg-2 scopes; openid+profile+email are leg-1 surface and not the Gmail
        // connection's audit trail).
        String gmailScopes = scopes.stream()
                .filter(s -> s.startsWith(GMAIL_SCOPE_PREFIX))
                .sorted()
                .collect(Collectors.joining(" "));

        // 4. Bind tenant ScopedValue BEFORE opening the transaction (Pitfall 6: Hibernate
        //    captures tenant on JPA session open, not on call site). Mirror
        //    OAuthProvisioningService.createTenantAndUser ordering (lines 78-86).
        UUID tenantId = currentUser.getTenantId();
        UUID userId = currentUser.getId();
        String email = leg2.getEmail();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> tx.executeWithoutResult(status -> {
                    byte[] envelope = cipher.encrypt(
                            refreshToken.getBytes(StandardCharsets.UTF_8),
                            tenantId.toString());
                    connections.upsert(tenantId, email, gmailScopes, envelope);
                    UserEntity refreshed = users.findById(userId).orElseThrow();
                    refreshed.advanceTo(OnboardingStep.GMAIL_CONNECTED);
                    users.save(refreshed);
                }));

        // 5. Structured event log — NEVER include subject/email/token contents
        //    (Threat T-1.4-02-token-leak / D-E1).
        log.info("event=gmail_connection_upserted tenantId={}", tenantId);

        super.onAuthenticationSuccess(req, res, auth);
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
