package com.zeromail.api.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Single {@link org.springframework.security.web.authentication.AuthenticationFailureHandler}
 * bean wired into {@code SecurityConfig.oauth2Login().failureHandler(...)}. Dispatch
 * criterion is the THROWN EXCEPTION TYPE (not the registration id — the
 * {@link AuthenticationException} surface does not always carry the source
 * registration; reading it from the failed authorization request is also
 * Spring-internal-API).
 *
 * <p>For Phase 1.4 the only Gmail-specific failure paths are:
 * <ul>
 *   <li>{@link GmailIdentityMismatchException} — thrown by
 *       {@link GmailOAuthSuccessHandler} on subject-mismatch.</li>
 *   <li>{@link OAuth2AuthenticationException} with error code {@code access_denied}
 *       — Google returns this on consent-denied.</li>
 * </ul>
 *
 * <p>Both route to {@link GmailOAuthFailureHandler}. Future provider-specific
 * failure handlers can be added the same way.
 */
@Component
public class OAuth2LoginDispatchingFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final GmailOAuthFailureHandler gmailFailure;

    public OAuth2LoginDispatchingFailureHandler(GmailOAuthFailureHandler gmailFailure) {
        this.gmailFailure = gmailFailure;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException, ServletException {
        if (ex instanceof GmailIdentityMismatchException) {
            gmailFailure.onAuthenticationFailure(req, res, ex);
            return;
        }
        if (ex instanceof OAuth2AuthenticationException oae
                && "access_denied".equals(oae.getError().getErrorCode())) {
            gmailFailure.onAuthenticationFailure(req, res, ex);
            return;
        }
        super.onAuthenticationFailure(req, res, ex);
    }
}
