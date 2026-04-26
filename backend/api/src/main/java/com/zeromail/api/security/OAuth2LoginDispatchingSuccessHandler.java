package com.zeromail.api.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Single {@link org.springframework.security.web.authentication.AuthenticationSuccessHandler}
 * bean wired into {@code SecurityConfig.oauth2Login().successHandler(...)}. Spring Security
 * 7's {@code oauth2Login()} DSL accepts ONE success handler — per-registration dispatch
 * has to happen inside it (RESEARCH §Pattern 1, Pitfall 1).
 *
 * <p>Routes by {@link OAuth2AuthenticationToken#getAuthorizedClientRegistrationId()}:
 * <ul>
 *   <li>{@code "google"} — first-login leg → existing {@link GoogleOAuthSuccessHandler}
 *       (Phase 1.1 invariants preserved).</li>
 *   <li>{@code "google-gmail"} — incremental Gmail grant leg → new
 *       {@link GmailOAuthSuccessHandler}.</li>
 *   <li>anything else — fail loudly; it means OAuth registration config drifted.</li>
 * </ul>
 */
@Component
public class OAuth2LoginDispatchingSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final GoogleOAuthSuccessHandler googleHandler;
    private final GmailOAuthSuccessHandler gmailHandler;

    public OAuth2LoginDispatchingSuccessHandler(
            GoogleOAuthSuccessHandler googleHandler,
            GmailOAuthSuccessHandler gmailHandler) {
        this.googleHandler = googleHandler;
        this.gmailHandler = gmailHandler;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException, ServletException {
        if (auth instanceof OAuth2AuthenticationToken token) {
            switch (token.getAuthorizedClientRegistrationId()) {
                case "google" -> googleHandler.onAuthenticationSuccess(req, res, auth);
                case "google-gmail" -> gmailHandler.onAuthenticationSuccess(req, res, auth);
                default -> throw new IllegalStateException(
                        "Unsupported OAuth registration: " + token.getAuthorizedClientRegistrationId());
            }
        } else {
            super.onAuthenticationSuccess(req, res, auth);
        }
    }
}
