package com.zeromail.core.chat.projection;

import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import java.time.Instant;
import java.util.UUID;

public record ChatMessageProjection(
        UUID id, ChatRole role, ChatMessageParts parts, Instant createdAt) {}
