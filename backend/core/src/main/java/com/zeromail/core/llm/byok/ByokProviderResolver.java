package com.zeromail.core.llm.byok;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.domain.KeyFormat;
import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.llm.usecases.LlmCredentialSource;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import com.zeromail.core.llm.usecases.ResolvedLlmProviderCredential;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ByokProviderResolver {

    private static final long FALLBACK_PROVIDER_CATALOG_VERSION = 1L;

    private final UserByokKeyRepository userByokKeyRepository;
    private final RefreshTokenCipher refreshTokenCipher;

    public ByokProviderResolver(
            UserByokKeyRepository userByokKeyRepository, RefreshTokenCipher refreshTokenCipher) {
        this.userByokKeyRepository = userByokKeyRepository;
        this.refreshTokenCipher = refreshTokenCipher;
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedLlmProviderCredential> resolve(UUID tenantId) {
        return userByokKeyRepository
                .findByTenantId(tenantId)
                .filter(this::isActivationEligible)
                .map(userByokKey -> toResolvedCredential(tenantId, userByokKey));
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedLlmProviderCredential> resolve(
            UUID tenantId, String ignoredFallbackModel) {
        return resolve(tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedLlmProviderCredential> resolveForChat(
            UUID tenantId, String ignoredRequestedModel) {
        return resolve(tenantId);
    }

    private boolean isActivationEligible(UserByokKeyEntity userByokKey) {
        return userByokKey.isActive()
                && userByokKey.getModelId() != null
                && !userByokKey.getModelId().isBlank()
                && userByokKey.getLastTestResult() == UserByokKeyEntity.LastTestResult.OK;
    }

    private ResolvedLlmProviderCredential toResolvedCredential(
            UUID tenantId, UserByokKeyEntity userByokKey) {
        LlmProvider provider = LlmProvider.fromId(userByokKey.getProviderId());
        KeyFormat keyFormat = LlmProvider.defaultKeyFormat(provider);
        if (keyFormat == null) {
            throw new IllegalStateException("No BYOK key format configured for " + provider.id());
        }
        byte[] plaintextKey =
                refreshTokenCipher.decrypt(userByokKey.getApiKeyCiphertext(), tenantId.toString());
        try {
            return new ResolvedLlmProviderCredential(
                    provider.id(),
                    userByokKey.getModelId(),
                    new LlmProviderCredential(
                            provider.id(),
                            keyFormat.id(),
                            userByokKey.getBaseUrl(),
                            plaintextKey,
                            LlmCredentialSource.BYOK),
                    providerSecretVersion(userByokKey),
                    FALLBACK_PROVIDER_CATALOG_VERSION);
        } finally {
            Arrays.fill(plaintextKey, (byte) 0);
        }
    }

    private static long providerSecretVersion(UserByokKeyEntity userByokKey) {
        if (userByokKey.getUpdatedAt() == null) {
            return 1L;
        }
        return Math.max(1L, userByokKey.getUpdatedAt().toEpochMilli());
    }
}
