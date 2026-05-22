package com.zeromail.core.llm.usecases;

import com.zeromail.core.billing.domain.CallSite;
import java.util.Objects;
import java.util.UUID;

public record LlmUsageRecord(
        UUID tenantId,
        CallSite callSite,
        String provider,
        String modelId,
        String credentialSource,
        LlmUsage usage,
        int chargedCredits) {

    public LlmUsageRecord {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(callSite, "callSite");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(credentialSource, "credentialSource");
        Objects.requireNonNull(usage, "usage");
        if (chargedCredits < 0) {
            throw new IllegalArgumentException("chargedCredits must not be negative");
        }
    }
}
