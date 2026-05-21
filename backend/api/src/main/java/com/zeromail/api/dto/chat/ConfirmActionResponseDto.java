package com.zeromail.api.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = "state")
public record ConfirmActionResponseDto(String state) {}
