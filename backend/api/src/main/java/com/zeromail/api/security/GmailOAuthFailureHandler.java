package com.zeromail.api.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.zeromail.api.config.ZeroMailApiProperties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Failure handler for the Gmail leg of OAuth2 login (D-A3, D-D4). Distinguishes two
 * failure modes via thrown-exception subtype / OAuth2Error code:
 *
 * <ul>
 *   <li><b>Identity mismatch</b> — {@link GmailIdentityMismatchException} thrown by
 *       {@link GmailOAuthSuccessHandler} BEFORE persistence. Removes the stale
 *       {@code OAuth2AuthorizedClient} (Pitfall 2 / threat T-1.4-02-stale-grant —
 *       Spring persisted it before the success handler ran), best-effort revokes the
 *       captured token via {@link GoogleTokenRevocationClient}, then redirects
 *       {@code /onboarding?error=gmail_identity_mismatch}.</li>
 *   <li><b>Consent denied</b> — Google returns {@code error=access_denied} when the
 *       user declines the consent screen; Spring wraps it as
 *       {@link OAuth2AuthenticationException}. No token to revoke (Google never
 *       issued one). Redirects {@code /onboarding?error=gmail_consent_denied}.</li>
 *   <li>Anything else — defer to {@link SimpleUrlAuthenticationFailureHandler}'s
 *       default behavior so existing failure semantics for non-Gmail flows stay
 *       untouched.</li>
 * </ul>
 *
 * <p><b>Privacy contract (D-E1):</b> log statements emit only opaque event names +
 * {@code outcome=...}. Token bytes never appear in any log. Even on revoke
 * 5xx the redirect proceeds (best-effort cleanup per CONTEXT §Specifics).
 */
@Component
public class GmailOAuthFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GmailOAuthFailureHandler.class);
    private static final String GMAIL_REGISTRATION_ID = "google-gmail";

    private final OAuth2AuthorizedClientService authorizedClients;
    private final GoogleTokenRevocationClient revoker;
    private final String onboardingUrl;

    public GmailOAuthFailureHandler(
            OAuth2AuthorizedClientService authorizedClients,
            GoogleTokenRevocationClient revoker,
            ZeroMailApiProperties properties) {
        this.authorizedClients = authorizedClients;
        this.revoker = revoker;
        this.onboardingUrl = stripTrailingSlash(properties.web().baseUrl().toString()) + "/onboarding";
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException, ServletException {
        if (ex instanceof GmailIdentityMismatchException mismatch) {
            // Pitfall 2 / threat T-1.4-02-stale-grant: AuthorizedClient was already
            // saved by Spring before the success handler threw. Drop it before the
            // user-facing redirect so a future flow does not pick up the orphaned grant.
            try {
                authorizedClients.removeAuthorizedClient(GMAIL_REGISTRATION_ID, currentPrincipalName(req));
            } catch (Exception ignored) {
                // Best-effort. removeAuthorizedClient may no-op if the principal
                // name does not match; log nothing — privacy contract.
            }
            // Best-effort revoke (Pitfall 8: never retry on the same token).
            revoker.revokeQuietly(mismatch.getAccessTokenToRevoke());

            log.info("event=gmail_identity_mismatch outcome=rejected");
            getRedirectStrategy().sendRedirect(req, res, onboardingUrl + "?error=gmail_identity_mismatch");
            return;
        }
        if (ex instanceof OAuth2AuthenticationException oae
                && "access_denied".equals(oae.getError().getErrorCode())) {
            log.info("event=gmail_consent_denied outcome=user_declined");
            getRedirectStrategy().sendRedirect(req, res, onboardingUrl + "?error=gmail_consent_denied");
            return;
        }
        // Other failures: defer to default behavior.
        super.onAuthenticationFailure(req, res, ex);
    }

    /**
     * Best-effort principal-name resolution for the {@code removeAuthorizedClient}
     * call. The failed auth token may not be in {@code SecurityContextHolder} at
     * this point (Spring may have cleared it on failure). Falls back to a sentinel
     * so {@code removeAuthorizedClient} no-ops cleanly when the principal name
     * does not match a stored client.
     */
    private static String currentPrincipalName(HttpServletRequest req) {
        return req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "anonymous";
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
