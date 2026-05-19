package com.zeromail.core.chat.llm.springai;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.usecases.ZeroMailChatProperties;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.gateway.springai.admin.ProviderMasterKeyResolver;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class SpringAiChatModelFactory {

    private static final String PLATFORM_PROVIDER_ID = "platform";

    private final ZeroMailCoreProperties zeroMailCoreProperties;
    private final ZeroMailChatProperties chatProperties;
    private final AssistantSettingsJpaRepository assistantSettingsRepository;
    private final TenantByokCredentialsRepository tenantByokCredentialsRepository;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ProviderMasterKeyResolver providerMasterKeyResolver;
    private final ConcurrentMap<ChatModelCacheKey, StreamingChatModel> chatModelsByKey =
            new ConcurrentHashMap<>();

    public SpringAiChatModelFactory(
            ZeroMailCoreProperties zeroMailCoreProperties,
            ZeroMailChatProperties chatProperties,
            AssistantSettingsJpaRepository assistantSettingsRepository,
            TenantByokCredentialsRepository tenantByokCredentialsRepository,
            RefreshTokenCipher refreshTokenCipher,
            ProviderMasterKeyResolver providerMasterKeyResolver) {
        this.zeroMailCoreProperties = zeroMailCoreProperties;
        this.chatProperties = chatProperties;
        this.assistantSettingsRepository = assistantSettingsRepository;
        this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
        this.refreshTokenCipher = refreshTokenCipher;
        this.providerMasterKeyResolver = providerMasterKeyResolver;
    }

    public StreamingChatModel forTenant(String tenantId) {
        UUID tenantUuid = UUID.fromString(tenantId);
        AssistantSettingsEntity assistantSettings =
                assistantSettingsRepository
                        .findByTenantId(tenantUuid)
                        .orElseGet(() -> AssistantSettingsEntity.defaults(tenantUuid));
        String providerId = providerId(assistantSettings);
        String modelId = modelId(assistantSettings);
        LlmProvider platformProvider = platformProvider();
        Optional<ProviderMasterKeyResolver.ResolvedMasterKey> platformMasterKey =
                PLATFORM_PROVIDER_ID.equals(providerId)
                        ? providerMasterKeyResolver.resolveOptional(platformProvider)
                        : Optional.empty();
        long providerSecretVersion =
                platformMasterKey
                        .map(ProviderMasterKeyResolver.ResolvedMasterKey::providerSecretVersion)
                        .orElseGet(
                                () ->
                                        providerMasterKeyResolver.providerSecretVersionOrZero(
                                                platformProvider));
        ChatModelCacheKey cacheKey =
                new ChatModelCacheKey(
                        tenantId,
                        "chat",
                        PLATFORM_PROVIDER_ID.equals(providerId)
                                ? platformProvider.id()
                                : providerId,
                        modelId,
                        providerSecretVersion);
        return chatModelsByKey.computeIfAbsent(
                cacheKey,
                ignored -> {
                    if (PLATFORM_PROVIDER_ID.equals(providerId)) {
                        return platformModel(modelId, platformMasterKey.orElse(null));
                    }
                    return byokModel(tenantUuid, providerId, modelId);
                });
    }

    public void evictByProvider(LlmProvider provider) {
        chatModelsByKey.keySet().removeIf(cacheKey -> cacheKey.providerId().equals(provider.id()));
    }

    private StreamingChatModel platformModel(
            String modelId, ProviderMasterKeyResolver.ResolvedMasterKey resolvedMasterKey) {
        ZeroMailCoreProperties.ZeroMailLlmProperties llmProperties =
                zeroMailCoreProperties.llm().platform();
        String baseUrl = llmProperties.baseUrl();
        String apiKey = llmProperties.apiKey();
        if (resolvedMasterKey != null) {
            if (resolvedMasterKey.keyFormat() != KeyFormat.OPENAI_FORMAT) {
                throw new IllegalStateException(
                        "Assistant streaming platform provider requires OpenAI-compatible format");
            }
            baseUrl = resolvedMasterKey.baseUrl();
            apiKey = new String(resolvedMasterKey.plaintextKey(), StandardCharsets.UTF_8);
        }
        return OpenAiChatModel.builder()
                .options(
                        OpenAiChatOptions.builder()
                                .baseUrl(baseUrl)
                                .apiKey(apiKey)
                                .model(modelId)
                                .temperature(0.2)
                                .timeout(llmProperties.readTimeout())
                                .internalToolExecutionEnabled(false)
                                .build())
                .build();
    }

    private StreamingChatModel byokModel(UUID tenantId, String providerId, String modelId) {
        TenantByokCredentialsEntity credentials =
                tenantByokCredentialsRepository
                        .findByTenantId(tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No BYOK credentials configured for tenant"));
        if (credentials.getProvider() != BYOKProvider.OPENAI) {
            throw new IllegalStateException(
                    "Assistant streaming BYOK provider is not supported yet: " + providerId);
        }
        byte[] decryptedKey =
                refreshTokenCipher.decrypt(credentials.getEncryptedKey(), tenantId.toString());
        String plaintextApiKey = new String(decryptedKey, StandardCharsets.UTF_8);
        String endpoint =
                credentials.getEndpoint() == null || credentials.getEndpoint().isBlank()
                        ? zeroMailCoreProperties.llm().platform().baseUrl()
                        : credentials.getEndpoint();
        try {
            return OpenAiChatModel.builder()
                    .options(
                            OpenAiChatOptions.builder()
                                    .apiKey(plaintextApiKey)
                                    .baseUrl(endpoint)
                                    .model(modelId)
                                    .temperature(0.2)
                                    .internalToolExecutionEnabled(false)
                                    .build())
                    .build();
        } finally {
            java.util.Arrays.fill(decryptedKey, (byte) 0);
        }
    }

    private String providerId(AssistantSettingsEntity assistantSettings) {
        String providerId = assistantSettings.getProviderId();
        return providerId == null || providerId.isBlank() ? PLATFORM_PROVIDER_ID : providerId;
    }

    private String modelId(AssistantSettingsEntity assistantSettings) {
        String modelId = assistantSettings.getDefaultModel();
        return modelId == null || modelId.isBlank() ? chatProperties.defaultModel() : modelId;
    }

    private LlmProvider platformProvider() {
        String baseUrl = zeroMailCoreProperties.llm().platform().baseUrl();
        if (baseUrl != null && baseUrl.contains("openrouter.ai")) {
            return LlmProvider.OPENROUTER;
        }
        BYOKProvider provider = zeroMailCoreProperties.llm().platform().provider();
        if (provider == BYOKProvider.ANTHROPIC) {
            return LlmProvider.ANTHROPIC;
        }
        if (provider == BYOKProvider.GOOGLE_GENAI) {
            return LlmProvider.GOOGLE;
        }
        if (provider == BYOKProvider.DEEPSEEK) {
            return LlmProvider.DEEPSEEK;
        }
        return LlmProvider.OPENAI;
    }

    private record ChatModelCacheKey(
            String tenantId,
            String feature,
            String providerId,
            String modelId,
            long providerSecretVersion) {}
}
