package com.zeromail.core.chat.llm.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.chat.persistence.AssistantSettingsJpaRepository;
import com.zeromail.core.chat.usecases.ChatProperties;
import com.zeromail.core.llm.byok.ByokProviderResolver;
import com.zeromail.core.llm.gateway.springai.SpringAiProviderChatClientFactory;
import com.zeromail.core.llm.usecases.LlmCredentialSource;
import com.zeromail.core.llm.usecases.LlmProviderCredential;
import com.zeromail.core.llm.usecases.PlatformLlmRuntimeRouter;
import com.zeromail.core.llm.usecases.ResolvedLlmProviderCredential;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

class SpringAiChatModelFactoryTest {

    @Test
    void platform_chat_client_is_created_from_resolved_openai_compatible_route() {
        SpringAiChatModelFactory factory =
                factoryForRoute(
                        "ROUTER_9R",
                        "OPENAI_FORMAT",
                        "https://9router.zeromail.vn/v1",
                        "cx/gpt-5.5");

        SpringAiChatModelFactory.ResolvedChatClient resolvedChatClient =
                factory.forTenant(UUID.randomUUID().toString(), "cx/gpt-5.5");

        assertThat(resolvedChatClient.providerId()).isEqualTo("ROUTER_9R");
        assertThat(resolvedChatClient.keyFormat()).isEqualTo("OPENAI_FORMAT");
        assertThat(resolvedChatClient.credentialSource()).isEqualTo(LlmCredentialSource.PLATFORM);
        assertThat(resolvedChatClient.modelId()).isEqualTo("cx/gpt-5.5");
    }

    @Test
    void platform_chat_client_is_created_from_resolved_anthropic_compatible_route() {
        SpringAiChatModelFactory factory =
                factoryForRoute(
                        "ROUTER_9R",
                        "ANTHROPIC_FORMAT",
                        "https://anthropic-router.example/v1",
                        "claude-sonnet-4-20250514");

        SpringAiChatModelFactory.ResolvedChatClient resolvedChatClient =
                factory.forTenant(UUID.randomUUID().toString(), "claude-sonnet-4-20250514");

        assertThat(resolvedChatClient.providerId()).isEqualTo("ROUTER_9R");
        assertThat(resolvedChatClient.keyFormat()).isEqualTo("ANTHROPIC_FORMAT");
        assertThat(resolvedChatClient.credentialSource()).isEqualTo(LlmCredentialSource.PLATFORM);
        assertThat(resolvedChatClient.modelId()).isEqualTo("claude-sonnet-4-20250514");
    }

    private static SpringAiChatModelFactory factoryForRoute(
            String providerId, String keyFormat, String baseUrl, String modelId) {
        AssistantSettingsJpaRepository settingsRepository =
                mock(AssistantSettingsJpaRepository.class);
        when(settingsRepository.findByTenantId(any())).thenReturn(Optional.empty());

        PlatformLlmRuntimeRouter platformRuntimeRouter = mock(PlatformLlmRuntimeRouter.class);
        when(platformRuntimeRouter.resolveRoutes(any(), eq(modelId)))
                .thenReturn(
                        List.of(
                                new ResolvedLlmProviderCredential(
                                        providerId,
                                        modelId,
                                        new LlmProviderCredential(
                                                providerId,
                                                keyFormat,
                                                baseUrl,
                                                "test-key".getBytes(StandardCharsets.UTF_8),
                                                LlmCredentialSource.PLATFORM),
                                        7,
                                        11)));

        SpringAiProviderChatClientFactory chatClientFactory =
                mock(SpringAiProviderChatClientFactory.class);
        when(chatClientFactory.create(any(), eq(modelId), eq(0.2), eq(2048), eq(false)))
                .thenReturn(mock(ChatClient.class));

        SpringAiChatModelFactory factory =
                new SpringAiChatModelFactory(
                        new ChatProperties(
                                15,
                                30,
                                modelId,
                                4096,
                                1024,
                                4,
                                1,
                                new ChatProperties.HistoryProperties(50),
                                new ChatProperties.TokenizerProperties(4)),
                        settingsRepository,
                        platformRuntimeRouter,
                        mock(ByokProviderResolver.class),
                        chatClientFactory);

        ArgumentCaptor<LlmProviderCredential> credentialCaptor =
                ArgumentCaptor.forClass(LlmProviderCredential.class);
        factory.forTenant(UUID.randomUUID().toString(), modelId);
        verify(chatClientFactory)
                .create(credentialCaptor.capture(), eq(modelId), eq(0.2), eq(2048), eq(false));
        assertThat(credentialCaptor.getValue().providerId()).isEqualTo(providerId);
        assertThat(credentialCaptor.getValue().keyFormat()).isEqualTo(keyFormat);
        assertThat(credentialCaptor.getValue().baseUrl()).isEqualTo(baseUrl);
        return factory;
    }
}
