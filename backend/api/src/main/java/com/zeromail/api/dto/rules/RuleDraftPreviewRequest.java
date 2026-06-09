package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(requiredProperties = {"gmailConnectionId", "compiled"})
public record RuleDraftPreviewRequest(
        @NotNull UUID gmailConnectionId,
        @Valid @NotNull CompiledPayloadRequest compiled,
        Integer sampleSize,
        Boolean evaluateSemanticIntents) {

    public boolean evaluateSemanticIntentsFlag() {
        return Boolean.TRUE.equals(evaluateSemanticIntents);
    }
}
