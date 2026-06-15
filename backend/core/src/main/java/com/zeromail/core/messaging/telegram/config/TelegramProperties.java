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
        URI apiBaseUrl,
        URI webBaseUrl,
        boolean webhookAutoRegister,
        URI webhookPublicBaseUrl) {

    private static final URI DEFAULT_API_BASE_URL = URI.create("https://api.telegram.org");
    private static final URI DEFAULT_WEB_BASE_URL = URI.create("http://localhost:3000");
    private static final String WEBHOOK_PATH = "/api/integrations/telegram/webhook";

    public TelegramProperties {
        botToken = normalize(botToken);
        botUsername = normalizeUsername(botUsername);
        botAccountId = normalizeBotAccountId(botAccountId, botUsername);
        webhookSecretToken = normalize(webhookSecretToken);
        messagingLinkSecret = normalize(messagingLinkSecret);
        apiBaseUrl = apiBaseUrl == null ? DEFAULT_API_BASE_URL : apiBaseUrl;
        webBaseUrl = webBaseUrl == null ? DEFAULT_WEB_BASE_URL : webBaseUrl;
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

    /**
     * True when the API process should self-register its Telegram webhook on startup. Requires the
     * channel to be {@link #configured()} and a public base URL to build the callback from.
     */
    public boolean webhookAutoRegisterEnabled() {
        return webhookAutoRegister && configured() && webhookPublicBaseUrl != null;
    }

    /**
     * Public HTTPS URL Telegram should POST updates to, derived from {@link #webhookPublicBaseUrl}.
     * Returns {@code null} when no public base URL is configured. Trailing slashes on the base are
     * stripped so the path is not doubled.
     */
    public URI webhookUrl() {
        if (webhookPublicBaseUrl == null) {
            return null;
        }
        String base = webhookPublicBaseUrl.toString();
        String trimmedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(trimmedBase + WEBHOOK_PATH);
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
