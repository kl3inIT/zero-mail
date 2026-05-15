package com.zeromail.api.security;

import com.zeromail.api.config.ZeroMailApiProperties;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.account.usecases.OAuthProvisioningService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Bundled-scope OAuth2 success handler (Phase 01.5 D-A1, D-A2, D-B2).
 *
 * <p>Replaces the previous two-handler dispatch ({@code OAuth2LoginDispatchingSuccessHandler} →
 * {@code GoogleOAuthSuccessHandler} / {@code GmailOAuthSuccessHandler}) with a single handler that
 * performs all scope validation, null-refresh-token checks, and provisioning delegation in one
 * pass. Deleted two-leg files per D-A3.
 *
 * <p><b>All-or-nothing policy (D-A2):</b>
 *
 * <ol>
 *   <li>If {@code gmail.modify} is absent from granted scopes → throw {@link
 *       OAuth2AuthenticationException}{@code ("gmail_scope_required")} BEFORE any DB write. Failure
 *       handler redirects to {@code /login?error=gmail_scope_required}. (1a) Pre-throw cleanup:
 *       before throwing, {@code removeAuthorizedClient("google", token.getName())} is called so the
 *       {@code LoginRedirectAuthenticationFailureHandler}'s best-effort cleanup is no longer the
 *       sole cleanup path (CR-02 fix).
 *   <li>If refresh token is null AND no existing user → throw {@link
 *       OAuth2AuthenticationException}{@code ("consent_denied")} BEFORE any DB write. Failure
 *       handler redirects to {@code /login?error=consent_denied} (MED-3 fix). Pre-throw cleanup
 *       applied here too (CR-02 fix).
 *   <li>If only {@code gmail.settings.basic} is missing (but {@code gmail.modify} present) → emit
 *       opaque warning log {@code event=oauth_settings_basic_missing} and proceed (INFO-7:
 *       settings.basic is forward-looking for Phase 3, not v1-blocking).
 *   <li>On full / partial-OK grant → delegate to {@link
 *       OAuthProvisioningService#provisionBundledOAuth} which atomically writes user + tenant +
 *       GmailConnectionEntity in ONE transaction (HIGH-1 fix).
 * </ol>
 *
 * <p><b>Privacy contract (D-E1, T-01.5-01-02, T-01.5-01-03):</b> log statements emit ONLY opaque
 * event names + tenantId UUID — never email, Google subject, or token bytes.
 */
@Component
public class GoogleOAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthSuccessHandler.class);

    private final OAuthProvisioningService provisioningService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository;

    public GoogleOAuthSuccessHandler(
            OAuthProvisioningService provisioningService,
            OAuth2AuthorizedClientService authorizedClientService,
            UserRepository userRepository,
            ZeroMailApiProperties properties) {
        this.provisioningService = provisioningService;
        this.authorizedClientService = authorizedClientService;
        this.userRepository = userRepository;

        // Validate baseUrl scheme/host at construction time so a misconfigured
        // ZEROMAIL_WEB_BASE_URL fails fast instead of silently becoming an open-redirect on
        // the OAuth success path. Same-origin deployments only — http/https schemes; host
        // must be present (no opaque or relative URIs).
        java.net.URI baseUrl = properties.web().baseUrl();
        String scheme = baseUrl.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new IllegalStateException(
                    "zero-mail.api.web.base-url must use http or https scheme; got: " + scheme);
        }
        if (baseUrl.getHost() == null || baseUrl.getHost().isBlank()) {
            throw new IllegalStateException(
                    "zero-mail.api.web.base-url must have a non-blank host");
        }

        // Build the success-redirect URL via UriComponentsBuilder so URI semantics are
        // preserved regardless of input shape.
        String onboardingUrl =
                UriComponentsBuilder.fromUri(baseUrl).path("/onboarding").build().toUriString();
        setDefaultTargetUrl(onboardingUrl);
    }

    @Override
    public void onAuthenticationSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Authentication authentication)
            throws IOException, ServletException {
        OAuth2AuthenticationToken authenticationToken = (OAuth2AuthenticationToken) authentication;
        OidcUser oidcUser =
                Objects.requireNonNull(
                        (OidcUser) authenticationToken.getPrincipal(),
                        "OIDC principal is required");
        String googleSubject =
                Objects.requireNonNull(
                        oidcUser.getClaimAsString(StandardClaimNames.SUB),
                        "OIDC subject is required");
        String email =
                Objects.requireNonNull(
                        oidcUser.getClaimAsString(StandardClaimNames.EMAIL),
                        "OIDC email is required");

        // Load the authorized client once (all checks reuse this single load).
        OAuth2AuthorizedClient authorizedClient =
                authorizedClientService.loadAuthorizedClient(
                        "google", authenticationToken.getName());

        // (a) Granted-scope check BEFORE any DB write.
        if (authorizedClient == null
                || authorizedClient.getAccessToken() == null
                || !authorizedClient
                        .getAccessToken()
                        .getScopes()
                        .contains(OAuthScopes.GMAIL_MODIFY)) {
            // Clean up any partial AuthorizedClient Spring stored before the success handler ran.
            // Use authenticationToken.getName() — always available from OAuth2AuthenticationToken,
            // unlike request.getUserPrincipal() which is null here (principal not committed to
            // SecurityContext yet). Best-effort; never log the principal name (privacy).
            if (authorizedClient != null) {
                try {
                    authorizedClientService.removeAuthorizedClient(
                            "google", authenticationToken.getName());
                } catch (Exception ignored) {
                    // Best-effort — failure to clean up is non-fatal for the error redirect.
                }
            }
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            "gmail_scope_required", "Required Gmail scope was not granted", null));
        }

        // (b) Null refresh-token check.
        if (authorizedClient.getRefreshToken() == null) {
            // First-login null: no existing user → throw consent_denied.
            boolean existingUser = userRepository.findByGoogleSubject(googleSubject).isPresent();
            if (!existingUser) {
                log.info("event=oauth_no_refresh_token_first_login");
                // Clean up partial AuthorizedClient before throwing.
                // authenticationToken.getName() is always available; request.getUserPrincipal()
                // is null here.
                try {
                    authorizedClientService.removeAuthorizedClient(
                            "google", authenticationToken.getName());
                } catch (Exception ignored) {
                    /* best-effort */
                }
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(
                                "consent_denied",
                                "Refresh token was not issued — user must re-authenticate with consent",
                                null));
            }
            // Reconnect null: fall through to provisioning (service handles gracefully).
        }

        // (c) settings.basic observational warning (non-blocking).
        boolean settingsBasicMissing =
                !authorizedClient
                        .getAccessToken()
                        .getScopes()
                        .contains(OAuthScopes.GMAIL_SETTINGS_BASIC);
        if (settingsBasicMissing) {
            log.warn("event=oauth_settings_basic_missing");
        }

        // (d) Build gmail-scoped scope string for audit storage.
        String gmailScopes =
                authorizedClient.getAccessToken().getScopes().stream()
                        .filter(scope -> scope.startsWith(OAuthScopes.GMAIL_PREFIX))
                        .sorted()
                        .collect(Collectors.joining(" "));

        // (e) Null-safe refresh-token extraction (reconnect path).
        String refreshToken =
                authorizedClient.getRefreshToken() == null
                        ? null
                        : authorizedClient.getRefreshToken().getTokenValue();

        // (f) Single delegation to atomic provisioning service.
        OAuthProvisioningService.BundledProvisioningResult result =
                provisioningService.provisionBundledOAuth(
                        googleSubject, email, refreshToken, gmailScopes);

        // (g) Follow-up with tenant context (after provisioning resolves tenantId).
        if (settingsBasicMissing) {
            log.warn("event=oauth_settings_basic_missing tenantId={}", result.tenantId());
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
