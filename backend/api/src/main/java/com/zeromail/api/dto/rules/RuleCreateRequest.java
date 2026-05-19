package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"displayName", "sourceText", "compiled"})
public record RuleCreateRequest(
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Size(max = 4000) String sourceText,
        @Valid @NotNull CompiledPayloadRequest compiled) {}
