package com.zeromail.api.dto.chat;

import com.zeromail.core.chat.usecases.ChatStreamCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(requiredProperties = {"chatId", "userText"})
public record ChatStreamRequestDto(
        @NotNull UUID chatId, @NotBlank String userText, String modelOverride) {

    public ChatStreamCommand toCommand(String tenantId) {
        return new ChatStreamCommand(tenantId, chatId, userText, modelOverride);
    }
}
