package com.zeromail.api.dto.chat;

import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.projection.ChatHistoryDetail;
import com.zeromail.core.chat.projection.ChatMessageProjection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatHistoryDetailResponseDto(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<ChatMessageDto> messages) {

    public static ChatHistoryDetailResponseDto from(ChatHistoryDetail detail) {
        return new ChatHistoryDetailResponseDto(
                detail.id(),
                detail.title(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.messages().stream().map(ChatMessageDto::from).toList());
    }

    public record ChatMessageDto(
            UUID id, ChatRole role, ChatMessageParts parts, Instant createdAt) {

        static ChatMessageDto from(ChatMessageProjection projection) {
            return new ChatMessageDto(
                    projection.id(), projection.role(), projection.parts(), projection.createdAt());
        }
    }
}
