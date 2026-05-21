package com.zeromail.core.chat.domain.event;

import java.time.Instant;
import java.util.UUID;

@SuppressWarnings("unused")
public record AssistantSendCompleted(
        String tenantId, UUID chatId, String toolCallId, UUID auditId, Instant sentAt) {

    public AssistantSendCompleted {
        tenantId = requireText(tenantId, "tenantId");
        if (chatId == null) {
            throw new IllegalArgumentException("chatId must not be null");
        }
        toolCallId = requireText(toolCallId, "toolCallId");
        if (auditId == null) {
            throw new IllegalArgumentException("auditId must not be null");
        }
        if (sentAt == null) {
            throw new IllegalArgumentException("sentAt must not be null");
        }
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }
}
