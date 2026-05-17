package com.zeromail.api.dto.rules;

public record RuleEnabledPreviewRequest(Integer sampleSize, Boolean evaluateSemanticIntents) {

    public boolean evaluateSemanticIntentsFlag() {
        return Boolean.TRUE.equals(evaluateSemanticIntents);
    }
}
