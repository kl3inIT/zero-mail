package com.zeromail.core.chat.llm.springai;

import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.persistence.TenantByokCredentialsEntity;
import com.zeromail.core.llm.persistence.TenantByokCredentialsRepository;
import java.nio.charset.StandardCharsets;
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
    private final AssistantSettingsJpaRepository assistantSettingsRepository;
    private final TenantByokCredentialsRepository tenantByokCredentialsRepository;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ConcurrentMap<ChatModelCacheKey, StreamingChatModel> chatModelsByKey =
            new ConcurrentHashMap<>();

    public SpringAiChatModelFactory(
            ZeroMailCoreProperties zeroMailCoreProperties,
            AssistantSettingsJpaRepository assistantSettingsRepository,
            TenantByokCredentialsRepository tenantByokCredentialsRepository,
            RefreshTokenCipher refreshTokenCipher) {
        this.zeroMailCoreProperties = zeroMailCoreProperties;
        this.assistantSettingsRepository = assistantSettingsRepository;
        this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
        this.refreshTokenCipher = refreshTokenCipher;
    }

    public StreamingChatModel forTenant(String tenantId) {
        UUID tenantUuid = UUID.fromString(tenantId);
        AssistantSettingsEntity assistantSettings =
                assistantSettingsRepository
                        .findByTenantId(tenantUuid)
                        .orElseGet(() -> AssistantSettingsEntity.defaults(tenantUuid));
        String providerId = providerId(assistantSettings);
        String modelId = modelId(assistantSettings);
        ChatModelCacheKey cacheKey = new ChatModelCacheKey(tenantId, providerId, modelId);
        return chatModelsByKey.computeIfAbsent(
                cacheKey,
                ignored -> {
                    if (PLATFORM_PROVIDER_ID.equals(providerId)) {
                        return platformModel(modelId);
                    }
                    return byokModel(tenantUuid, providerId, modelId);
                });
    }

    private StreamingChatModel platformModel(String modelId) {
        ZeroMailCoreProperties.ZeroMailLlmProperties llmProperties =
                zeroMailCoreProperties.llm().platform();
        return OpenAiChatModel.builder()
                .options(
                        OpenAiChatOptions.builder()
                                .baseUrl(llmProperties.baseUrl())
                                .apiKey(llmProperties.apiKey())
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
        return modelId == null || modelId.isBlank()
                ? zeroMailCoreProperties.llm().platform().compileModel()
                : modelId;
    }

    private record ChatModelCacheKey(String tenantId, String providerId, String modelId) {}
}
