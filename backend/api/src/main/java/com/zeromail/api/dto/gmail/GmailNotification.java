package com.zeromail.api.dto.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailNotification(
        String emailAddress,
        @JsonDeserialize(using = FlexibleLongDeserializer.class)
        Long historyId) {}
