package com.zeromail.api.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class PubSubOidcAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PubSubOidcAuthFilter.class);

    private final TokenVerifier tokenVerifier;
    private final String expectedEmail;

    public PubSubOidcAuthFilter(String audience, String saEmail, String certsUrl) {
        this.expectedEmail = saEmail;
        this.tokenVerifier = TokenVerifier.newBuilder()
                .setAudience(audience)
                .setIssuer("https://accounts.google.com")
                .setCertificatesLocation(certsUrl)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/internal/pubsub/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("event=pubsub_oidc_missing_token");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            JsonWebSignature jws = tokenVerifier.verify(authHeader.substring(7));
            String email = (String) jws.getPayload().get("email");
            if (!expectedEmail.equalsIgnoreCase(email)) {
                log.warn("event=pubsub_oidc_wrong_email");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            request.setAttribute("pubsub.verified.email", email);
            chain.doFilter(request, response);
        } catch (TokenVerifier.VerificationException e) {
            log.warn("event=pubsub_oidc_verification_failed");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
