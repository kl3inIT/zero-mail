package com.zeromail.api.dto.rules;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RuleUpdateRequest(
    @NotBlank @Size(max = 160) String displayName,
    @NotBlank @Size(max = 4000) String sourceText,
    @Valid @NotNull CompiledPayloadRequest compiled,
    @NotNull @PositiveOrZero Integer entityVersion) {}
