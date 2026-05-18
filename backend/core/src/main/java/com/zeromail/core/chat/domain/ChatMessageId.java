package com.zeromail.core.chat.domain;

import java.util.UUID;

public record ChatMessageId(UUID value) {

    public ChatMessageId {
        if (value == null) {
            throw new IllegalArgumentException("chat message id value is required");
        }
    }
}
