package com.zeromail.core.rules.domain;

public record SemanticIntentMatcher(String nodeId, String intent, boolean deferred)
        implements MatcherNode {

    public SemanticIntentMatcher {
        requireText(nodeId, "nodeId");
        requireText(intent, "intent");
        if (!deferred) {
            throw new IllegalArgumentException("SEMANTIC_INTENT matchers must be deferred");
        }
    }

    @Override
    public MatcherType type() {
        return MatcherType.SEMANTIC_INTENT;
    }

    @Override
    public boolean requiresBodyEvidence() {
        return false;
    }

    private static void requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
