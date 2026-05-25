package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.domain.ChatMessage;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChatStreamRequest(
        String tenantId,
        UUID chatId,
        String modelId,
        String systemPrompt,
        ChatToolCatalog toolCatalog,
        List<ChatMessage> conversationHistory,
        Map<String, String> transientToolResponseJsonByCallId) {

    public ChatStreamRequest(
            String tenantId,
            UUID chatId,
            String modelId,
            String systemPrompt,
            ChatToolCatalog toolCatalog,
            List<ChatMessage> conversationHistory) {
        this(tenantId, chatId, modelId, systemPrompt, toolCatalog, conversationHistory, Map.of());
    }

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
        transientToolResponseJsonByCallId =
                transientToolResponseJsonByCallId == null
                        ? Map.of()
                        : Map.copyOf(transientToolResponseJsonByCallId);
    }
}
