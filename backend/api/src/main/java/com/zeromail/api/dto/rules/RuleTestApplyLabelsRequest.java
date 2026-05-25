package com.zeromail.api.dto.rules;

public record RuleTestApplyLabelsRequest(Integer sampleSize, Boolean evaluateSemanticIntents) {

    public boolean evaluateSemanticIntentsFlag() {
        return Boolean.TRUE.equals(evaluateSemanticIntents);
    }
}
