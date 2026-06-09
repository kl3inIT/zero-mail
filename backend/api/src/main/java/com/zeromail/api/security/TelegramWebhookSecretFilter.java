package com.zeromail.api.security;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class TelegramWebhookSecretFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramProperties telegramProperties;

    public TelegramWebhookSecretFilter(TelegramProperties telegramProperties) {
        this.telegramProperties = telegramProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!telegramProperties.configured()) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
            return;
        }
        String suppliedSecret = request.getHeader(HEADER_NAME);
        if (!constantTimeEquals(suppliedSecret, telegramProperties.webhookSecretToken())) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String suppliedSecret, String expectedSecret) {
        if (suppliedSecret == null || expectedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                suppliedSecret.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
