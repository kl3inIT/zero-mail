package com.zeromail.api.dto.chat;

import com.zeromail.core.chat.usecases.ChatStreamCommand;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ChatStreamRequestDto(UUID chatId, @NotBlank String userText, String modelOverride) {

    public ChatStreamCommand toCommand(String tenantId) {
        return new ChatStreamCommand(tenantId, chatId, userText, modelOverride);
    }
}
