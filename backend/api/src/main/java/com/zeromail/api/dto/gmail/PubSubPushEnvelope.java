package com.zeromail.api.dto.gmail;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PubSubPushEnvelope(PubSubMessage message, String subscription) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubSubMessage(
            String data,
            String messageId,
            String publishTime,
            Map<String, String> attributes) {}
}
