package com.zeromail.api.dto.rules;

public record RulePreviewRequest(Integer sampleSize, Boolean evaluateSemanticIntents) {

    public boolean evaluateSemanticIntentsFlag() {
        return Boolean.TRUE.equals(evaluateSemanticIntents);
    }
}
