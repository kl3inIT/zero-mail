package com.zeromail.core.messaging.telegram.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUserPayload(
        @JsonProperty("id") long id,
        @JsonProperty("username") String username,
        @JsonProperty("language_code") String languageCode) {}
