package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.domain.ChatMessage;
import java.util.List;
import java.util.UUID;

public record ChatStreamRequest(
        String tenantId,
        UUID chatId,
        String modelId,
        String systemPrompt,
        ChatToolCatalog toolCatalog,
        List<ChatMessage> conversationHistory) {

    public ChatStreamRequest {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (chatId == null) {
            throw new IllegalArgumentException("chatId must not be null");
        }
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        if (toolCatalog == null) {
            throw new IllegalArgumentException("toolCatalog must not be null");
        }
        conversationHistory =
                conversationHistory == null ? List.of() : List.copyOf(conversationHistory);
    }
}
