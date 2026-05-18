package com.zeromail.api.dto.chat;

import com.zeromail.core.chat.projection.ChatHistoryProjection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatHistoryListResponseDto(
        List<ChatHistorySummaryDto> chats, int pageSize, int pageOffset) {

    public static ChatHistoryListResponseDto from(
            List<ChatHistoryProjection> projections, int pageSize, int pageOffset) {
        return new ChatHistoryListResponseDto(
                projections.stream().map(ChatHistorySummaryDto::from).toList(),
                pageSize,
                pageOffset);
    }

    public record ChatHistorySummaryDto(
            UUID id, String title, Instant updatedAt, int messageCount) {

        static ChatHistorySummaryDto from(ChatHistoryProjection projection) {
            return new ChatHistorySummaryDto(
                    projection.id(),
                    projection.title(),
                    projection.updatedAt(),
                    projection.messageCount());
        }
    }
}
