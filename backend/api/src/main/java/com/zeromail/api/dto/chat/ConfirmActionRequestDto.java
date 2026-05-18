package com.zeromail.api.dto.chat;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ConfirmActionRequestDto(
        @NotBlank String toolCallId,
        Map<String, Object> contentOverride,
        boolean vipAcknowledged) {}
