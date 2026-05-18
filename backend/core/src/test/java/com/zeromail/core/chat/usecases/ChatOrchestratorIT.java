package com.zeromail.core.chat.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.Disposable;

@SuppressWarnings("SqlResolve")
class ChatOrchestratorIT extends PostgresContainerTest {

    @Autowired ChatOrchestrator chatOrchestrator;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ChatLlmGateway chatLlmGateway;

    @Test
    void first_turn_persists_user_message_before_stream_and_bootstraps_chat_title()
            throws Exception {
        UUID tenantId = seedTenant();
        String userText =
                "Please summarize unread customer messages that need founder attention today";
        RecordingChatStreamSink streamSink = new RecordingChatStreamSink();
        when(chatLlmGateway.streamChat(any(ChatStreamRequest.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            ChatStreamSink modelSink = invocation.getArgument(1);
                            modelSink.emitTextStart("assistant-text");
                            modelSink.emitTextDelta("assistant-text", "Done.");
                            modelSink.emitTextEnd("assistant-text");
                            modelSink.emitFinish("stop");
                            return (Disposable) () -> {};
                        });

        Disposable streamSubscription =
                withTenant(
                        tenantId,
                        () ->
                                chatOrchestrator.stream(
                                        new ChatStreamCommand(
                                                tenantId.toString(), null, userText, null),
                                        streamSink));

        assertThat(streamSubscription).isNotNull();
        assertThat(streamSink.awaitFinish()).isTrue();

        UUID chatId =
                jdbcTemplate.queryForObject(
                        "select id from chat where tenant_id = ?", UUID.class, tenantId);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select title from chat where id = ?", String.class, chatId))
                .isEqualTo("Please summarize unread customer message");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from chat_message where chat_id = ? and role = 'user'",
                                Integer.class,
                                chatId))
                .isEqualTo(1);
        assertThat(streamSink.events().getFirst()).isEqualTo("data-persistence:user-message-saved");
    }

    @Test
    void confirmable_tool_call_persists_pending_action_for_confirm_endpoint() throws Exception {
        UUID tenantId = seedTenant();
        RecordingChatStreamSink streamSink = new RecordingChatStreamSink();
        when(chatLlmGateway.streamChat(any(ChatStreamRequest.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            ChatStreamSink modelSink = invocation.getArgument(1);
                            modelSink.emitToolInputStart("tool-send-pending", "sendEmail");
                            modelSink.emitToolInputAvailable(
                                    "tool-send-pending",
                                    "sendEmail",
                                    """
                                    {"to":"founder@example.test","subject":"Follow-up","body":"User-authored follow-up body"}
                                    """);
                            modelSink.emitFinish("tool-call");
                            return (Disposable) () -> {};
                        });

        withTenant(
                tenantId,
                () ->
                        chatOrchestrator.stream(
                                new ChatStreamCommand(
                                        tenantId.toString(), null, "Draft a follow-up email", null),
                                streamSink));

        assertThat(streamSink.awaitFinish()).isTrue();
        UUID chatId =
                jdbcTemplate.queryForObject(
                        "select id from chat where tenant_id = ?", UUID.class, tenantId);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select state
                                  from assistant_pending_action
                                 where tenant_id = ?
                                   and chat_id = ?
                                   and tool_call_id = 'tool-send-pending'
                                """,
                                String.class,
                                tenantId,
                                chatId))
                .isEqualTo("PENDING");
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select draft_body
                                  from assistant_pending_action
                                 where tenant_id = ?
                                   and chat_id = ?
                                   and tool_call_id = 'tool-send-pending'
                                """,
                                String.class,
                                tenantId,
                                chatId))
                .isEqualTo("User-authored follow-up body");
        assertThat(streamSink.events()).contains("finish:awaiting-confirmation");
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "orchestrator-first-turn");
        return tenantId;
    }

    private static <T> T withTenant(UUID tenantId, TenantCallable<T> tenantCallable)
            throws Exception {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantCallable::call);
    }

    @FunctionalInterface
    private interface TenantCallable<T> {
        T call() throws Exception;
    }
}
