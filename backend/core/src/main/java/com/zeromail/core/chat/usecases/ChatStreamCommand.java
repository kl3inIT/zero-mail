package com.zeromail.core.chat.usecases;

import java.util.UUID;

@SuppressWarnings("unused")
public record ChatStreamCommand(
        String tenantId, UUID chatId, String userText, String modelOverride) {

    public ChatStreamCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("userText must not be blank");
        }
    }
}
