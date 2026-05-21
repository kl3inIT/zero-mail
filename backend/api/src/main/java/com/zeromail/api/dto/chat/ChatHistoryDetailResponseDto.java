package com.zeromail.api.dto.chat;

import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.DataErrorPart;
import com.zeromail.core.chat.domain.parts.Part;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.projection.ChatHistoryDetail;
import com.zeromail.core.chat.projection.ChatMessageProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(requiredProperties = {"id", "title", "createdAt", "updatedAt", "messages"})
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

    @Schema(requiredProperties = {"id", "role", "parts", "createdAt"})
    public record ChatMessageDto(
            UUID id, ChatRole role, ChatMessagePartsDto parts, Instant createdAt) {

        static ChatMessageDto from(ChatMessageProjection projection) {
            return new ChatMessageDto(
                    projection.id(),
                    projection.role(),
                    ChatMessagePartsDto.from(projection.parts()),
                    projection.createdAt());
        }
    }

    @Schema(requiredProperties = {"schemaVersion", "parts"})
    public record ChatMessagePartsDto(int schemaVersion, List<ChatPartDto> parts) {

        static ChatMessagePartsDto from(ChatMessageParts chatMessageParts) {
            return new ChatMessagePartsDto(
                    chatMessageParts.schemaVersion(),
                    chatMessageParts.parts().stream().map(ChatPartDto::from).toList());
        }
    }

    @Schema(requiredProperties = {"type"})
    public record ChatPartDto(
            String type,
            String partId,
            String text,
            Instant completedAt,
            String toolCallId,
            String toolName,
            String state,
            Map<String, Object> input,
            Map<String, Object> output,
            Map<String, Object> confirmation,
            boolean truncated,
            String errorMessage) {

        static ChatPartDto from(Part part) {
            return switch (part) {
                case TextPart textPart ->
                        new ChatPartDto(
                                "text",
                                textPart.partId(),
                                textPart.text(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                null);
                case AssistantTextPart assistantTextPart ->
                        new ChatPartDto(
                                "assistant-text",
                                assistantTextPart.partId(),
                                assistantTextPart.text(),
                                assistantTextPart.completedAt(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                null);
                case ToolCallPart toolCallPart ->
                        new ChatPartDto(
                                "tool-" + toolCallPart.toolName(),
                                toolCallPart.partId(),
                                null,
                                null,
                                toolCallPart.toolCallId(),
                                toolCallPart.toolName(),
                                toolCallPart.state(),
                                emptyToNull(toolCallPart.inputJson()),
                                null,
                                null,
                                toolCallPart.truncated(),
                                null);
                case ToolOutputPart toolOutputPart ->
                        new ChatPartDto(
                                "tool-" + toolOutputPart.toolName(),
                                toolOutputPart.partId(),
                                null,
                                null,
                                toolOutputPart.toolCallId(),
                                toolOutputPart.toolName(),
                                toolOutputPart.state(),
                                emptyToNull(toolOutputPart.inputJson()),
                                emptyToNull(toolOutputPart.outputJson()),
                                emptyToNull(toolOutputPart.confirmationJson()),
                                toolOutputPart.truncated(),
                                null);
                case DataErrorPart dataErrorPart ->
                        new ChatPartDto(
                                "data-error",
                                dataErrorPart.partId(),
                                null,
                                null,
                                dataErrorPart.toolCallId(),
                                dataErrorPart.toolName(),
                                null,
                                null,
                                null,
                                null,
                                false,
                                dataErrorPart.errorMessage());
            };
        }

        private static Map<String, Object> emptyToNull(Map<String, Object> value) {
            return value == null || value.isEmpty() ? null : value;
        }
    }
}
