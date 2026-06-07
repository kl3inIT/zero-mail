package com.zeromail.core.messaging.telegram.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(
        @JsonProperty("update_id") long updateId,
        @JsonProperty("message") TelegramMessagePayload message) {}
