package com.zeromail.core.messaging.telegram.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramApiResponse(boolean ok, String description) {}
