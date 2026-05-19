package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = "compiled")
public record RuleDraftPreviewRequest(
        @Valid @NotNull CompiledPayloadRequest compiled,
        Integer sampleSize,
        Boolean evaluateSemanticIntents) {

    public boolean evaluateSemanticIntentsFlag() {
        return Boolean.TRUE.equals(evaluateSemanticIntents);
    }
}
