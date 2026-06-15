package com.zeromail.core.messaging.telegram.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Payload for Telegram {@code setWebhook}. {@code allowedUpdates} is restricted to the update kinds
 * this bot actually handles ({@code message} for chat, {@code callback_query} for the
 * confirm/cancel buttons) so Telegram does not deliver noise. {@code secretToken} is echoed back by
 * Telegram in the {@code X-Telegram-Bot-Api-Secret-Token} header on every update and verified by
 * the webhook filter.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelegramSetWebhookRequest(
        @JsonProperty("url") String url,
        @JsonProperty("secret_token") String secretToken,
        @JsonProperty("allowed_updates") List<String> allowedUpdates) {

    public static TelegramSetWebhookRequest of(String url, String secretToken) {
        return new TelegramSetWebhookRequest(
                url, secretToken, List.of("message", "callback_query"));
    }
}
