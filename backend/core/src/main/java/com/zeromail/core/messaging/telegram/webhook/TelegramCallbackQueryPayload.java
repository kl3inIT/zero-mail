package com.zeromail.core.messaging.telegram.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramCallbackQueryPayload(
        @JsonProperty("id") String id,
        @JsonProperty("from") TelegramUserPayload from,
        @JsonProperty("message") TelegramMessagePayload message,
        @JsonProperty("data") String data) {}
