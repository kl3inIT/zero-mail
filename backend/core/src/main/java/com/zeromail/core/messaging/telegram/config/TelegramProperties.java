package com.zeromail.core.messaging.telegram.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zero-mail.messaging.telegram")
public record TelegramProperties(
        boolean enabled,
        String botToken,
        String botUsername,
        String botAccountId,
        String webhookSecretToken,
        String messagingLinkSecret,
        URI apiBaseUrl) {

    private static final URI DEFAULT_API_BASE_URL = URI.create("https://api.telegram.org");

    public TelegramProperties {
        botToken = normalize(botToken);
        botUsername = normalizeUsername(botUsername);
        botAccountId = normalizeBotAccountId(botAccountId, botUsername);
        webhookSecretToken = normalize(webhookSecretToken);
        messagingLinkSecret = normalize(messagingLinkSecret);
        apiBaseUrl = apiBaseUrl == null ? DEFAULT_API_BASE_URL : apiBaseUrl;
    }

    public boolean configured() {
        return enabled
                && !botToken.isBlank()
                && !botUsername.isBlank()
                && !webhookSecretToken.isBlank()
                && !messagingLinkSecret.isBlank();
    }

    public String botApiBaseUrl() {
        return apiBaseUrl.toString() + "/bot" + botToken;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeUsername(String value) {
        String normalized = normalize(value);
        return normalized.startsWith("@") ? normalized.substring(1) : normalized;
    }

    private static String normalizeBotAccountId(String value, String botUsername) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return botUsername == null || botUsername.isBlank() ? "telegram-bot" : botUsername;
    }
}
