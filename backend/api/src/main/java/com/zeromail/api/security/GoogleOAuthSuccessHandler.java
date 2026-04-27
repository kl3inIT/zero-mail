package com.zeromail.api.security;

import java.io.IOException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.zeromail.api.config.ZeroMailApiProperties;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.account.service.OAuthProvisioningService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bundled-scope OAuth2 success handler (Phase 01.5 D-A1, D-A2, D-B2).
 *
 * <p>Replaces the previous two-handler dispatch ({@code OAuth2LoginDispatchingSuccessHandler}
 * → {@code GoogleOAuthSuccessHandler} / {@code GmailOAuthSuccessHandler}) with a single
 * handler that performs all scope validation, null-refresh-token checks, and provisioning
 * delegation in one pass. Deleted two-leg files per D-A3.
 *
 * <p><b>All-or-nothing policy (D-A2):</b>
 * <ol>
 *   <li>If {@code gmail.modify} is absent from granted scopes → throw
 *       {@link OAuth2AuthenticationException}{@code ("gmail_scope_required")} BEFORE any DB
 *       write. Failure handler redirects to {@code /login?error=gmail_scope_required}.
 *       (1a) Pre-throw cleanup: before throwing,
 *       {@code removeAuthorizedClient("google", token.getName())} is called so the
 *       {@code LoginRedirectAuthenticationFailureHandler}'s best-effort cleanup is no longer
 *       the sole cleanup path (CR-02 fix).</li>
 *   <li>If refresh token is null AND no existing user → throw
 *       {@link OAuth2AuthenticationException}{@code ("consent_denied")} BEFORE any DB write.
 *       Failure handler redirects to {@code /login?error=consent_denied} (MED-3 fix).
 *       Pre-throw cleanup applied here too (CR-02 fix).</li>
 *   <li>If only {@code gmail.settings.basic} is missing (but {@code gmail.modify} present)
 *       → emit opaque warning log {@code event=oauth_settings_basic_missing} and proceed
 *       (INFO-7: settings.basic is forward-looking for Phase 3, not v1-blocking).</li>
 *   <li>On full / partial-OK grant → delegate to
 *       {@link OAuthProvisioningService#provisionBundledOAuth} which atomically writes
 *       user + tenant + GmailConnectionEntity in ONE transaction (HIGH-1 fix).</li>
 * </ol>
 *
 * <p><b>Privacy contract (D-E1, T-01.5-01-02, T-01.5-01-03):</b> log statements emit ONLY
 * opaque event names + tenantId UUID — never email, Google subject, or token bytes.
 */
@Component
public class GoogleOAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthSuccessHandler.class);

    private final OAuthProvisioningService provisioning;
    private final OAuth2AuthorizedClientService authorizedClients;
    private final UserRepository users;

    public GoogleOAuthSuccessHandler(
            OAuthProvisioningService provisioning,
            OAuth2AuthorizedClientService authorizedClients,
            UserRepository users,
            ZeroMailApiProperties properties) {
        this.provisioning = provisioning;
        this.authorizedClients = authorizedClients;
        this.users = users;
        setDefaultTargetUrl(stripTrailingSlash(properties.web().baseUrl().toString()) + "/onboarding");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) auth;
        OidcUser oidc = (OidcUser) token.getPrincipal();
        String googleSubject = oidc.getSubject();
        String email = oidc.getEmail();

        // Load the authorized client once (all checks reuse this single load).
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient("google", token.getName());

        // (a) Granted-scope check BEFORE any DB write (D-A2, T-01.5-01-04).
        if (client == null || client.getAccessToken() == null
                || !client.getAccessToken().getScopes().contains(OAuthScopes.GMAIL_MODIFY)) {
            // CR-02 fix: clean up any partial AuthorizedClient Spring stored before the success
            // handler ran. Use token.getName() — always available from OAuth2AuthenticationToken,
            // unlike req.getUserPrincipal() which is null here (principal not committed to
            // SecurityContext yet). Best-effort; never log the principal name (privacy D-E1).
            if (client != null) {
                try {
                    authorizedClients.removeAuthorizedClient("google", token.getName());
                } catch (Exception ignored) {
                    // Best-effort — failure to clean up is non-fatal for the error redirect.
                }
            }
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("gmail_scope_required",
                            "Required Gmail scope was not granted", null));
        }

        // (b) Null refresh-token check (MED-3, T-01.5-01-10).
        if (client.getRefreshToken() == null) {
            // First-login null: no existing user → throw consent_denied.
            boolean existingUser = users.findByGoogleSubject(googleSubject).isPresent();
            if (!existingUser) {
                log.info("event=oauth_no_refresh_token_first_login");
                // CR-02 fix: clean up partial AuthorizedClient before throwing.
                // token.getName() is always available; req.getUserPrincipal() is null here.
                try {
                    authorizedClients.removeAuthorizedClient("google", token.getName());
                } catch (Exception ignored) { /* best-effort */ }
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("consent_denied",
                                "Refresh token was not issued — user must re-authenticate with consent", null));
            }
            // Reconnect null: fall through to provisioning (service handles gracefully).
        }

        // (c) INFO-7: settings.basic observational warning (non-blocking).
        boolean settingsBasicMissing = !client.getAccessToken().getScopes().contains(OAuthScopes.GMAIL_SETTINGS_BASIC);
        if (settingsBasicMissing) {
            log.warn("event=oauth_settings_basic_missing");
        }

        // (d) Build gmail-scoped scope string for audit storage.
        String gmailScopes = client.getAccessToken().getScopes().stream()
                .filter(s -> s.startsWith(OAuthScopes.GMAIL_PREFIX))
                .sorted()
                .collect(Collectors.joining(" "));

        // (e) Null-safe refresh-token extraction (MED-3 reconnect path).
        String refreshToken = client.getRefreshToken() == null
                ? null
                : client.getRefreshToken().getTokenValue();

        // (f) Single delegation to atomic provisioning service (HIGH-1 fix).
        OAuthProvisioningService.BundledProvisioningResult result =
                provisioning.provisionBundledOAuth(googleSubject, email, refreshToken, gmailScopes);

        // (g) INFO-7 follow-up with tenant context (after provisioning resolves tenantId).
        if (settingsBasicMissing) {
            log.warn("event=oauth_settings_basic_missing tenantId={}", result.tenantId());
        }

        super.onAuthenticationSuccess(req, res, auth);
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
