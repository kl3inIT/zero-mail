package com.zeromail.api.security;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

public class PubSubOidcAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PubSubOidcAuthFilter.class);

    private final PubSubTokenVerifier tokenVerifier;
    private final String expectedEmail;

    public PubSubOidcAuthFilter(String serviceAccountEmail, TokenVerifier tokenVerifier) {
        this(
                serviceAccountEmail,
                idToken -> {
                    JsonWebSignature verifiedToken = tokenVerifier.verify(idToken);
                    if (verifiedToken == null || verifiedToken.getPayload() == null) {
                        return null;
                    }
                    return (String) verifiedToken.getPayload().get("email");
                });
    }

    public PubSubOidcAuthFilter(String serviceAccountEmail, PubSubTokenVerifier tokenVerifier) {
        this.expectedEmail = serviceAccountEmail;
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getServletPath().startsWith("/internal/pubsub/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain)
            throws IOException, ServletException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("event=pubsub_oidc_missing_token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            String verifiedEmail = tokenVerifier.verifyEmail(authorizationHeader.substring(7));
            if (!expectedEmail.equalsIgnoreCase(verifiedEmail)) {
                log.warn("event=pubsub_oidc_wrong_email");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            request.setAttribute("pubsub.verified.email", verifiedEmail);
            chain.doFilter(request, response);
        } catch (TokenVerifier.VerificationException verificationException) {
            log.warn(
                    "event=pubsub_oidc_verification_failed type={}",
                    verificationException.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
