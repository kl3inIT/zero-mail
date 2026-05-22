package com.zeromail.core.llm.usecases;

import java.util.Map;
import java.util.Objects;

public record SemanticIntentEvaluationResult(Map<String, Boolean> matches, LlmUsage usage) {

    public SemanticIntentEvaluationResult {
        matches = Map.copyOf(Objects.requireNonNull(matches, "matches"));
        Objects.requireNonNull(usage, "usage");
    }
}
