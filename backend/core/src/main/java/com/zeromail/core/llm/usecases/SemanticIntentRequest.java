package com.zeromail.core.llm.usecases;

import java.util.Objects;

/**
 * Per-node semantic intent payload built by the triage orchestrator from a rule's {@code
 * SemanticIntentMatcher}.
 */
public record SemanticIntentRequest(String nodeId, String intent) {

    public SemanticIntentRequest {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(intent, "intent");
    }
}
