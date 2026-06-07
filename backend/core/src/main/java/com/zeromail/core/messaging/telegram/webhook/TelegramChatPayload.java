package com.zeromail.core.messaging.telegram.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChatPayload(@JsonProperty("id") long id, @JsonProperty("type") String type) {

    public boolean privateChat() {
        return "private".equals(type);
    }
}
