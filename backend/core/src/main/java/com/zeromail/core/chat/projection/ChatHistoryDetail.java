package com.zeromail.core.chat.projection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatHistoryDetail(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<ChatMessageProjection> messages) {

    public ChatHistoryDetail {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
