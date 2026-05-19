package com.zeromail.core.chat.domain;

import java.util.UUID;

public record ChatId(UUID value) {

    public ChatId {
        if (value == null) {
            throw new IllegalArgumentException("chat id value is required");
        }
    }
}
