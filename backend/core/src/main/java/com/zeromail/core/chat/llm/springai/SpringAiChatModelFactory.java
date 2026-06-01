package com.zeromail.core.chat.llm.springai;

import com.zeromail.core.chat.persistence.AssistantSettingsEntity;
import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.usecases.ChatProperties;
import com.zeromail.core.llm.byok.ByokProviderResolver;
import com.zeromail.core.llm.domain.LlmProvider;
import com.zeromail.core.llm.gateway.springai.SpringAiProviderChatClientFactory;
import com.zeromail.core.llm.routing.LlmRuntimeTask;
import com.zeromail.core.llm.usecases.LlmCredentialSource;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import com.zeromail.core.llm.usecases.PlatformLlmRuntimeRouter;
import com.zeromail.core.llm.usecases.ResolvedLlmProviderCredential;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

@Component
public class SpringAiChatModelFactory {

    private static final String PLATFORM_PROVIDER_ID = "platform";

    private final ChatProperties chatProperties;
    private final AssistantSettingsJpaRepository assistantSettingsRepository;
    private final PlatformLlmRuntimeRouter platformRuntimeRouter;
    private final ByokProviderResolver byokProviderResolver;
    private final SpringAiProviderChatClientFactory chatClientFactory;
    private final ConcurrentMap<ChatClientCacheKey, ResolvedChatClient> chatClientsByKey =
            new ConcurrentHashMap<>();

    public SpringAiChatModelFactory(
            ChatProperties chatProperties,
            AssistantSettingsJpaRepository assistantSettingsRepository,
            PlatformLlmRuntimeRouter platformRuntimeRouter,
            ByokProviderResolver byokProviderResolver,
            SpringAiProviderChatClientFactory chatClientFactory) {
        this.chatProperties = chatProperties;
        this.assistantSettingsRepository = assistantSettingsRepository;
        this.platformRuntimeRouter = platformRuntimeRouter;
        this.byokProviderResolver = byokProviderResolver;
        this.chatClientFactory = chatClientFactory;
    }

    public ResolvedChatClient forTenant(String tenantId, String requestedModelId) {
        UUID tenantUuid = UUID.fromString(tenantId);
        AssistantSettingsEntity assistantSettings =
                assistantSettingsRepository
                        .findByTenantId(tenantUuid)
                        .orElseGet(() -> AssistantSettingsEntity.defaults(tenantUuid));
        ResolvedLlmProviderCredential resolvedCredential =
                PLATFORM_PROVIDER_ID.equals(providerId(assistantSettings))
                        ? platformCredential(requestedModelId)
                        : byokCredential(tenantUuid, requestedModelId);
        ChatClientCacheKey cacheKey = cacheKey(tenantId, resolvedCredential);
        try {
            return chatClientsByKey.computeIfAbsent(
                    cacheKey, ignored -> createResolvedChatClient(resolvedCredential));
        } finally {
            resolvedCredential.credential().wipe();
        }
    }

    public void evictByProvider(LlmProvider provider) {
        chatClientsByKey.keySet().removeIf(cacheKey -> cacheKey.providerId().equals(provider.id()));
    }

    public void evictByModelIds(Collection<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return;
        }
        java.util.Set<String> affectedModelIds = java.util.Set.copyOf(modelIds);
        chatClientsByKey
                .keySet()
                .removeIf(cacheKey -> affectedModelIds.contains(cacheKey.modelId()));
    }

    public ChatOptions.Builder<?> optionsFor(ResolvedChatClient resolvedChatClient) {
        return chatClientFactory.options(
                resolvedChatClient.providerId(),
                resolvedChatClient.keyFormat(),
                resolvedChatClient.credentialSource(),
                resolvedChatClient.modelId(),
                0.2,
                2048,
                false);
    }

    private ResolvedLlmProviderCredential platformCredential(String requestedModelId) {
        ResolvedLlmProviderCredential primaryCredential =
                platformRuntimeRouter
                        .resolveRoutes(
                                LlmRuntimeTask.CHAT_ASSISTANT, modelIdOrDefault(requestedModelId))
                        .getFirst();
        String modelId = modelIdOrDefault(requestedModelId);
        if (modelId.equals(primaryCredential.modelId())) {
            return primaryCredential;
        }
        return new ResolvedLlmProviderCredential(
                primaryCredential.providerId(),
                modelId,
                primaryCredential.credential(),
                primaryCredential.providerSecretVersion(),
                primaryCredential.providerCatalogVersion());
    }

    private ResolvedLlmProviderCredential byokCredential(UUID tenantId, String requestedModelId) {
        Optional<ResolvedLlmProviderCredential> resolvedCredential =
                byokProviderResolver.resolveForChat(tenantId, modelIdOrDefault(requestedModelId));
        return resolvedCredential.orElseThrow(
                () -> new IllegalStateException("No BYOK credentials configured for tenant"));
    }

    private ResolvedChatClient createResolvedChatClient(
            ResolvedLlmProviderCredential resolvedCredential) {
        LlmProviderCredential credential = resolvedCredential.credential();
        ChatClient chatClient =
                chatClientFactory.create(
                        credential, resolvedCredential.modelId(), 0.2, 2048, false);
        return new ResolvedChatClient(
                chatClient,
                resolvedCredential.providerId(),
                credential.keyFormat(),
                credential.source(),
                resolvedCredential.modelId());
    }

    private ChatClientCacheKey cacheKey(
            String tenantId, ResolvedLlmProviderCredential resolvedCredential) {
        LlmProviderCredential credential = resolvedCredential.credential();
        return new ChatClientCacheKey(
                tenantId,
                "chat",
                resolvedCredential.providerId(),
                credential.keyFormat(),
                credential.baseUrl(),
                resolvedCredential.modelId(),
                credential.source(),
                resolvedCredential.providerSecretVersion(),
                resolvedCredential.providerCatalogVersion());
    }

    private String providerId(AssistantSettingsEntity assistantSettings) {
        String providerId = assistantSettings.getProviderId();
        return providerId == null || providerId.isBlank() ? PLATFORM_PROVIDER_ID : providerId;
    }

    private String modelIdOrDefault(String requestedModelId) {
        return requestedModelId == null || requestedModelId.isBlank()
                ? chatProperties.defaultModel()
                : requestedModelId;
    }

    public record ResolvedChatClient(
            ChatClient chatClient,
            String providerId,
            String keyFormat,
            LlmCredentialSource credentialSource,
            String modelId) {}

    private record ChatClientCacheKey(
            String tenantId,
            String feature,
            String providerId,
            String keyFormat,
            String baseUrl,
            String modelId,
            LlmCredentialSource credentialSource,
            long providerSecretVersion,
            long providerCatalogVersion) {}
}
