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
        // Read (and clear) the one-shot callback intent FIRST. An add/reconnect-mailbox attempt
        // comes
        // from an already-authenticated user inside the app, so its failures must keep the user IN
        // the
        // app (an in-app error toast) instead of bouncing them to the public /login page.
        boolean managementIntent = consumeManagementIntent(request);
        if (authenticationException instanceof OAuth2AuthenticationException oauthException) {
            switch (oauthException.getError().getErrorCode()) {
                case "access_denied", "consent_denied" -> {
                    redirectForFailure(
                            request,
                            response,
                            managementIntent,
                            "consent_denied",
                            "login_consent_denied");
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
                    redirectForFailure(
                            request,
                            response,
                            managementIntent,
                            "gmail_scope_required",
                            "login_gmail_scope_missing");
                    return;
                }
                case "mailbox_already_connected" -> {
                    // Add/reconnect-mailbox failure: the chosen Google account is already a
                    // CONNECTED
                    // mailbox for THIS workspace. Surface a specific message instead of the generic
                    // signin_failed fallback. No tenant/email logged (privacy contract).
                    redirectForFailure(
                            request,
                            response,
                            managementIntent,
                            "mailbox_already_connected",
                            "login_mailbox_already_connected");
                    return;
                }
                case "mailbox_in_other_workspace" -> {
                    // The chosen Google account is CONNECTED under a DIFFERENT workspace. Guide the
                    // user to
                    // free it there first. No tenant/email logged (privacy contract).
                    redirectForFailure(
                            request,
                            response,
                            managementIntent,
                            "mailbox_in_other_workspace",
                            "login_mailbox_in_other_workspace");
                    return;
                }
                default -> {
                    /* fall through */
                }
            }
        }
        if (managementIntent) {
            // Any other failure of an in-app add/reconnect attempt: still keep the authenticated
            // user in
            // the app with a generic error rather than the /login fallback.
            log.info("event=mailbox_management_oauth_failed");
            getRedirectStrategy()
                    .sendRedirect(request, response, buildMailboxErrorUrl("signin_failed"));
            return;
        }
        super.onAuthenticationFailure(request, response, authenticationException);
    }

    /**
     * Routes a mapped OAuth failure to the right surface: an authenticated add/reconnect-mailbox
     * attempt stays in-app ({@code /inbox?mailboxError=...}, rendered as a toast), while a
     * first-login failure goes to the public {@code /login?error=...} page. Event names stay opaque
     * (privacy contract) — no tenant, email, or token bytes.
     */
    private void redirectForFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            boolean managementIntent,
            String errorCode,
            String loginEventName)
            throws IOException {
        if (managementIntent) {
            log.info("event=mailbox_management_oauth_failed");
            getRedirectStrategy().sendRedirect(request, response, buildMailboxErrorUrl(errorCode));
        } else {
            log.info("event={}", loginEventName);
            getRedirectStrategy().sendRedirect(request, response, buildLoginUrl(errorCode));
        }
    }

    /**
     * Consumes the one-shot OAuth callback-intent snapshot and its initiating security context from
     * the session (so a stale intent cannot be replayed on a later callback) and reports whether
     * the failed attempt was an in-app mailbox-management intent ({@code add_mailbox} / {@code
     * reconnect_mailbox}).
     */
    private static boolean consumeManagementIntent(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object callbackIntentValue =
                session.getAttribute(OAuthIntentSnapshot.CALLBACK_INTENT_SESSION_ATTRIBUTE);
        session.removeAttribute(OAuthIntentSnapshot.CALLBACK_INTENT_SESSION_ATTRIBUTE);
        session.removeAttribute(OAuthIntentSnapshot.INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE);
        if (callbackIntentValue instanceof OAuthIntentSnapshot callbackIntentSnapshot) {
            return OAuthIntentSnapshot.INTENT_ADD_MAILBOX.equals(callbackIntentSnapshot.intent())
                    || OAuthIntentSnapshot.INTENT_RECONNECT_MAILBOX.equals(
                            callbackIntentSnapshot.intent());
        }
        return false;
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
     * Build an in-app redirect URL carrying a {@code ?mailboxError=} query param. Used when an
     * already-authenticated user's add/reconnect-mailbox OAuth attempt fails — they stay on the
     * mail app ({@code /inbox}) and the frontend surfaces the error as a toast, rather than being
     * kicked out to the public login page.
     */
    private String buildMailboxErrorUrl(String errorCode) {
        return UriComponentsBuilder.fromUri(properties.web().baseUrl())
                .path("/inbox")
                .queryParam("mailboxError", errorCode)
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
