package com.zeromail.core.messaging.telegram.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessagePayload(
        @JsonProperty("message_id") long messageId,
        @JsonProperty("from") TelegramUserPayload from,
        @JsonProperty("chat") TelegramChatPayload chat,
        @JsonProperty("text") String text) {}
