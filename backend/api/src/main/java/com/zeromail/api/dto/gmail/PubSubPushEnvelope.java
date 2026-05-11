package com.zeromail.api.dto.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PubSubPushEnvelope(PubSubMessage message, String subscription) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubSubMessage(
            String data, String messageId, String publishTime, Map<String, String> attributes) {}
}
