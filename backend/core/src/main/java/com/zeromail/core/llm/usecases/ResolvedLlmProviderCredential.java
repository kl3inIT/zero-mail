package com.zeromail.core.llm.usecases;

import java.util.Objects;

public record ResolvedLlmProviderCredential(
        String providerId,
        String modelId,
        LlmProviderCredential credential,
        long providerSecretVersion,
        long providerCatalogVersion) {

    public ResolvedLlmProviderCredential {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        Objects.requireNonNull(credential, "credential");
    }
}
