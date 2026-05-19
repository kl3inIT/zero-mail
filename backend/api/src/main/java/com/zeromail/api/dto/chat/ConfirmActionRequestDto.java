package com.zeromail.api.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Schema(requiredProperties = {"toolCallId", "vipAcknowledged"})
public record ConfirmActionRequestDto(
        @NotBlank String toolCallId,
        Map<String, Object> contentOverride,
        boolean vipAcknowledged) {}
