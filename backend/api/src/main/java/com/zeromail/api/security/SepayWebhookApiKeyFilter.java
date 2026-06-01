package com.zeromail.api.security;

import com.zeromail.core.billing.config.BillingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

public class SepayWebhookApiKeyFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH = "/api/plan-upgrades/webhooks/sepay";
    private static final String API_KEY_SCHEME = "Apikey ";

    private final BillingProperties.SepayProperties sepay;

    public SepayWebhookApiKeyFilter(BillingProperties billingProperties) {
        this.sepay = billingProperties.sepay();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !WEBHOOK_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!apiKeyVerified(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean apiKeyVerified(String authorizationHeader) {
        String webhookApiKey = sepay.webhookApiKey();
        if (webhookApiKey == null || authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }
        String expectedAuthorizationHeader = API_KEY_SCHEME + webhookApiKey;
        return MessageDigest.isEqual(
                expectedAuthorizationHeader.getBytes(StandardCharsets.UTF_8),
                authorizationHeader.trim().getBytes(StandardCharsets.UTF_8));
    }
}
