package com.zeromail.core.admin.mkey.persistence;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link LlmProviderMasterKeyEntity}. Pairs the LLM provider with an
 * internal per-key UUID so a single provider can hold multiple keys (priority-ordered failover).
 */
public record LlmProviderMasterKeyId(LlmProvider provider, UUID keyId) implements Serializable {

    public LlmProviderMasterKeyId {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(keyId, "keyId");
    }
}
