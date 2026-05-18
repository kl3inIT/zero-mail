package com.zeromail.core.chat.domain;

import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        UUID chatId,
        UUID tenantId,
        String role,
        ChatMessageParts parts,
        Instant createdAt) {}
