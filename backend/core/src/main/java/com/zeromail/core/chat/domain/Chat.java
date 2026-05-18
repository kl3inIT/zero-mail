package com.zeromail.core.chat.domain;

import java.time.Instant;

@SuppressWarnings("unused")
public record Chat(
        ChatId id,
        String tenantId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        Instant softDeletedAt) {

    public Chat {
        if (id == null) {
            throw new IllegalArgumentException("chat id is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenant id is required");
        }
    }
}
