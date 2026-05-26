package com.zeromail.core.chat.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.llm.springai.ZeroMailChatMemory;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.Disposable;

@SuppressWarnings("SqlResolve")
class ChatOrchestratorAssistantTextPersistenceIT extends PostgresContainerTest {

    @Autowired ChatOrchestrator chatOrchestrator;
    @Autowired ChatMessageJdbcRepository chatMessageRepository;
    @Autowired ZeroMailChatMemory zeroMailChatMemory;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ChatLlmGateway chatLlmGateway;

    @Test
    void assistant_text_persists_after_model_stream_finishes_and_reloads_into_memory()
            throws Exception {
        UUID tenantId = seedTenant();
        RecordingChatStreamSink streamSink = new RecordingChatStreamSink();
        when(chatLlmGateway.streamChat(any(ChatStreamRequest.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            ChatStreamSink modelSink = invocation.getArgument(1);
                            modelSink.emitTextStart("assistant-text");
                            modelSink.emitTextDelta("assistant-text", "Xin chào, ");
                            modelSink.emitTextDelta("assistant-text", "em là Zero Mail.");
                            modelSink.emitTextEnd("assistant-text");
                            modelSink.emitFinish("stop");
                            return (Disposable) () -> {};
                        });

        withTenant(
                tenantId,
                () ->
                        chatOrchestrator.stream(
                                new ChatStreamCommand(
                                        tenantId.toString(), null, "Introduce yourself", null),
                                streamSink));

        assertThat(streamSink.awaitFinish()).isTrue();
        UUID chatId =
                jdbcTemplate.queryForObject(
                        "select id from chat where tenant_id = ?", UUID.class, tenantId);
        UUID loadedChatId = Objects.requireNonNull(chatId);
        List<ChatMessage> messages =
                chatMessageRepository.findByChatIdAndTenantIdOrderByCreatedAtAsc(
                        tenantId, loadedChatId);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).role()).isEqualTo(ChatRole.ASSISTANT);
        AssistantTextPart assistantTextPart =
                (AssistantTextPart) messages.get(1).parts().parts().getFirst();
        assertThat(assistantTextPart.text()).isEqualTo("Xin chào, em là Zero Mail.");
        assertThat(streamSink.events())
                .containsSubsequence("data-persistence:assistant-message-saved", "finish:complete");

        List<org.springframework.ai.chat.messages.Message> memoryMessages =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> zeroMailChatMemory.get(loadedChatId.toString()));
        assertThat(memoryMessages).hasSize(2);
        assertThat(memoryMessages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(memoryMessages.get(1).getText()).isEqualTo("Xin chào, em là Zero Mail.");
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "orchestrator-assistant-text");
        return tenantId;
    }

    private static void withTenant(UUID tenantId, TenantRunnable tenantRunnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(tenantRunnable::run);
    }

    @FunctionalInterface
    private interface TenantRunnable {
        void run();
    }
}
