package com.zeromail.core.admin.mkey.usecases;

import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentialResolver;
import com.zeromail.core.llm.routing.PlatformLlmRouteCredentials;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProviderMasterKeyRouteCredentialResolver
        implements PlatformLlmRouteCredentialResolver {

    private final ProviderMasterKeyResolver providerMasterKeyResolver;

    public ProviderMasterKeyRouteCredentialResolver(
            ProviderMasterKeyResolver providerMasterKeyResolver) {
        this.providerMasterKeyResolver = providerMasterKeyResolver;
    }

    @Override
    public Optional<PlatformLlmRouteCredentials> resolve(String providerId, UUID keyId) {
        if (providerId == null || providerId.isBlank() || keyId == null) {
            return Optional.empty();
        }
        try {
            LlmProvider provider = LlmProvider.fromId(providerId);
            ProviderMasterKeyResolver.ResolvedMasterKey resolvedMasterKey =
                    providerMasterKeyResolver.resolve(provider, keyId);
            byte[] plaintextKey = resolvedMasterKey.plaintextKey();
            try {
                return Optional.of(
                        new PlatformLlmRouteCredentials(
                                provider.id(),
                                keyId,
                                plaintextKey,
                                resolvedMasterKey.keyFormat().id(),
                                resolvedMasterKey.baseUrl(),
                                resolvedMasterKey.providerSecretVersion(),
                                resolvedMasterKey.providerCatalogVersion()));
            } finally {
                Arrays.fill(plaintextKey, (byte) 0);
            }
        } catch (NoSuchElementException
                | ProviderMasterKeyResolver.MissingMasterKeyException missingRouteCredential) {
            return Optional.empty();
        }
    }
}
