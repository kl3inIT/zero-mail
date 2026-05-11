package com.zeromail.api.dto.rules;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RuleDraftPreviewRequest(
        @Valid @NotNull CompiledPayloadRequest compiled, Integer sampleSize) {}
