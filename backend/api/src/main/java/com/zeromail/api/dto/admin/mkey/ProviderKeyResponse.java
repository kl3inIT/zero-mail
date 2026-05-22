package com.zeromail.api.dto.admin.mkey;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.domain.MasterKeyStatus;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One platform-side credential row for an LLM provider. The admin detail page renders these as the
 * provider's priority-ordered failover chain. Carries the per-row identity, priority, lifecycle
 * status, operator label, and masked key preview — never the encrypted material or plaintext key.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "ProviderKey",
        description = "One platform credential row in a provider's failover chain",
        requiredProperties = {
            "provider",
            "keyId",
            "priority",
            "status",
            "providerSecretVersion",
            "createdAt"
        })
public record ProviderKeyResponse(
        @Schema(
                        allowableValues = {
                            "OPENAI",
                            "ANTHROPIC",
                            "GOOGLE",
                            "DEEPSEEK",
                            "OPENROUTER",
                            "ROUTER_9R"
                        })
                LlmProvider provider,
        UUID keyId,
        int priority,
        @Schema(allowableValues = {"PENDING", "ACTIVE", "REVOKED"}) MasterKeyStatus status,
        String label,
        @Schema(allowableValues = {"OPENAI_FORMAT", "ANTHROPIC_FORMAT", "GOOGLE_FORMAT"})
                KeyFormat keyFormat,
        String maskedKey,
        String baseUrl,
        long providerSecretVersion,
        Instant createdAt,
        Instant lastRotatedAt) {

    public static ProviderKeyResponse from(LlmProviderMasterKeyEntity entity) {
        return new ProviderKeyResponse(
                entity.getProvider(),
                entity.getKeyId(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getLabel(),
                entity.getKeyFormat(),
                entity.getMaskedKey(),
                entity.getBaseUrl(),
                entity.getProviderSecretVersion(),
                entity.getCreatedAt(),
                entity.getLastRotatedAt());
    }
}
