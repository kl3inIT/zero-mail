package com.zeromail.core.chat.domain;

import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
        ChatMessageId id,
        ChatId chatId,
        String tenantId,
        ChatRole role,
        ChatMessageParts parts,
        Instant createdAt) {

    public ChatMessage {
        if (chatId == null) {
            throw new IllegalArgumentException("chat id is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenant id is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("chat role is required");
        }
    }

    public ChatMessage(
            UUID id,
            UUID chatId,
            UUID tenantId,
            String role,
            ChatMessageParts parts,
            Instant createdAt) {
        this(
                id == null ? null : new ChatMessageId(id),
                new ChatId(chatId),
                requireTenantId(tenantId),
                ChatRole.fromId(role),
                parts,
                createdAt);
    }

    private static String requireTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant id is required");
        }
        return tenantId.toString();
    }
}
