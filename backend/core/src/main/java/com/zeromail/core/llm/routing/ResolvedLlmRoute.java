package com.zeromail.core.llm.routing;

import java.util.Objects;
import java.util.UUID;

public record ResolvedLlmRoute(
        LlmRuntimeTask task,
        LlmRoutingTier tier,
        String providerId,
        String modelId,
        UUID keyId,
        int keyPriority) {

    public ResolvedLlmRoute {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(tier, "tier");
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
    }
}
