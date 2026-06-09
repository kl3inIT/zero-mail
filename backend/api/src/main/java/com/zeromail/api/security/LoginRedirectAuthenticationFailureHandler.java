package com.zeromail.api.security;

import com.zeromail.api.config.ApiProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Single OAuth2 failure handler wired into {@code
 * SecurityConfig.oauth2Login().failureHandler(...)}. Replaces the two-handler dispatch chain
 * ({@code OAuth2LoginDispatchingFailureHandler} → {@code GmailOAuthFailureHandler}) deleted in
 * Phase 01.5 D-A3.
 *
 * <p>Failure semantics mapped to {@code /login?error=...} redirects (login-side, not
 * onboarding-side — bundled flow has no SIGNED_IN intermediate step):
 *
 * <ul>
 *   <li>{@code access_denied} — user denied the Google consent screen → {@code
 *       /login?error=consent_denied}.
 *   <li>{@code consent_denied} — null-refresh-token on first login (MED-3 path, thrown by {@link
 *       GoogleOAuthSuccessHandler}) → same redirect.
 *   <li>{@code gmail_scope_required} — {@code gmail.modify} not granted; best-effort {@code
 *       removeAuthorizedClient} + {@code /login?error=gmail_scope_required}.
 *   <li>Anything else (lost authorization request, bad state, provider error, etc.) — {@code
 *       super.onAuthenticationFailure} using the {@code defaultFailureUrl} set in the constructor
 *       ({@code /login?error=signin_failed}). Without that default, {@code
 *       SimpleUrlAuthenticationFailureHandler} would call {@code sendError(401)} and the browser
 *       would land on Spring's raw Whitelabel error page (WR-OAUTH fix).
 * </ul>
 *
 * <p><b>Privacy contract (D-E1, T-01.5-01-03):</b> log statements emit ONLY opaque event names —
 * never email, Google subject, OAuth error descriptions, or token bytes. No tenant exists at
 * failure time (login hasn't completed), so tenantId is also omitted.
 */
@Component
public class LoginRedirectAuthenticationFailureHandler
        extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log =
            LoggerFactory.getLogger(LoginRedirectAuthenticationFailureHandler.class);

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final ApiProperties properties;

    public LoginRedirectAuthenticationFailureHandler(
            OAuth2AuthorizedClientService authorizedClientService, ApiProperties properties) {
        this.authorizedClientService = authorizedClientService;
        this.properties = properties;
        // Default for any unmapped failure: redirect to the styled frontend login page instead of
        // SimpleUrlAuthenticationFailureHandler's sendError(401) → Spring Whitelabel error page.
        setDefaultFailureUrl(buildLoginUrl("signin_failed"));
    }

    @Override
    public void onAuthenticationFailure(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authenticationException)
            throws IOException, ServletException {
        var session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(OAuthIntentSnapshot.CALLBACK_INTENT_SESSION_ATTRIBUTE);
        }
        if (authenticationException instanceof OAuth2AuthenticationException oauthException) {
            switch (oauthException.getError().getErrorCode()) {
                case "access_denied", "consent_denied" -> {
                    log.info("event=login_consent_denied");
                    getRedirectStrategy()
                            .sendRedirect(request, response, buildLoginUrl("consent_denied"));
                    return;
                }
                case "gmail_scope_required" -> {
                    // Best-effort cleanup of any partial AuthorizedClient Spring may have stored
                    // before the exception was raised.
                    try {
                        authorizedClientService.removeAuthorizedClient(
                                "google", currentPrincipalName(request));
                    } catch (Exception _) {
                        // Best-effort — never log the principal name (privacy contract).
                    }
                    log.info("event=login_gmail_scope_missing");
                    getRedirectStrategy()
                            .sendRedirect(request, response, buildLoginUrl("gmail_scope_required"));
                    return;
                }
                default -> {
                    /* fall through to super */
                }
            }
        }
        super.onAuthenticationFailure(request, response, authenticationException);
    }

    /**
     * Build a /login redirect URL with an {@code ?error=} query param using {@link
     * UriComponentsBuilder} so URI semantics are preserved regardless of input shape. String concat
     * (the previous implementation) malformed the URL when {@code baseUrl} contained an existing
     * query string (WR-05 fix).
     */
    private String buildLoginUrl(String errorCode) {
        return UriComponentsBuilder.fromUri(properties.web().baseUrl())
                .path("/login")
                .queryParam("error", errorCode)
                .build()
                .toUriString();
    }

    /**
     * Best-effort principal-name resolution for the {@code removeAuthorizedClient} call. Falls back
     * to a sentinel so {@code removeAuthorizedClient} no-ops cleanly when the principal name does
     * not match any stored client.
     */
    private static String currentPrincipalName(HttpServletRequest request) {
        return request.getUserPrincipal() != null
                ? request.getUserPrincipal().getName()
                : "anonymous";
    }
}
