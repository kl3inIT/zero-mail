package com.zeromail.core.chat.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.Disposable;

/**
 * Verifies bounded transient-stream-failure retry inside the orchestrator's read-tool loop.
 *
 * <p>Background: the chat streaming path goes through Spring AI's {@code OpenAiChatModel} and the
 * OpenAI Java SDK's OkHttp transport. When the configured chat upstream (the self-hosted 9router
 * gateway at {@code https://9router.zeromail.vn/v1}) intermittently resets the HTTP/2 stream
 * mid-flight on multi-step turns, the SDK surfaces a {@code com.openai.errors.OpenAIIoException}
 * caused by {@code InterruptedIOException} caused by {@code StreamResetException(CANCEL)}. The
 * gateway classifies that family as transient and emits the {@code chat_stream_transient} error
 * code through {@link ChatStreamSink#emitError}; the orchestrator MUST retry once at the iteration
 * level, swallow the interim error so the FE never sees it, and bill credits only for the final
 * outcome.
 *
 * <p>This test substitutes the {@link ChatLlmGateway} with a Mockito mock that calls {@code
 * emitError("chat_stream_transient", ...)} on the first invocation and emits a clean assistant text
 * on the retry. It asserts that:
 *
 * <ol>
 *   <li>The gateway was invoked twice (one initial + one retry).
 *   <li>The downstream FE-facing sink never saw the {@code chat_stream_transient} error event (no
 *       {@code error:chat_stream_transient} in the recorded event list).
 *   <li>The assistant text from the retry was streamed through and persisted normally.
 * </ol>
 */
@SuppressWarnings("SqlResolve")
class ChatOrchestratorTransientStreamRetryIT extends PostgresContainerTest {

    @Autowired ChatOrchestrator chatOrchestrator;
    @Autowired ChatMessageJdbcRepository chatMessageRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ChatLlmGateway chatLlmGateway;

    @Test
    void transient_stream_cancel_is_retried_once_without_emitting_error_to_downstream_sink()
            throws Exception {
        UUID tenantId = seedTenant();
        AtomicInteger modelCallCount = new AtomicInteger();
        RecordingChatStreamSink streamSink = new RecordingChatStreamSink();
        when(chatLlmGateway.streamChat(any(ChatStreamRequest.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            int callNumber = modelCallCount.incrementAndGet();
                            ChatStreamSink modelSink = invocation.getArgument(1);
                            if (callNumber == 1) {
                                // Simulate the 9router HTTP/2 RST_STREAM(CANCEL) surfaced by the
                                // OpenAI Java SDK + OkHttp transport. The gateway has already
                                // classified the root cause as transient and converted it into the
                                // structured error code.
                                modelSink.emitError(
                                        "chat_stream_transient",
                                        "The assistant stream was interrupted. Retrying.");
                            } else {
                                modelSink.emitTextStart("assistant-text");
                                modelSink.emitTextDelta("assistant-text", "Đã thử lại");
                                modelSink.emitTextDelta("assistant-text", " thành công.");
                                modelSink.emitTextEnd("assistant-text");
                                modelSink.emitFinish("stop");
                            }
                            return (Disposable) () -> {};
                        });

        withTenant(
                tenantId,
                () ->
                        chatOrchestrator.stream(
                                new ChatStreamCommand(
                                        tenantId.toString(),
                                        null,
                                        "Hãy thử lại nếu transient",
                                        null),
                                streamSink));

        assertThat(streamSink.awaitFinish()).isTrue();
        assertThat(modelCallCount)
                .as("gateway must be invoked exactly twice (one transient failure + one retry)")
                .hasValue(2);
        assertThat(streamSink.events())
                .as(
                        "downstream sink must NOT see the transient error; the orchestrator buffers it during retry")
                .noneMatch(event -> event.startsWith("error:"))
                .anySatisfy(event -> assertThat(event).isEqualTo("text-delta:Đã thử lại"))
                .anySatisfy(event -> assertThat(event).isEqualTo("text-delta: thành công."))
                .anySatisfy(event -> assertThat(event).isEqualTo("finish:complete"));

        UUID chatId =
                jdbcTemplate.queryForObject(
                        "select id from chat where tenant_id = ?", UUID.class, tenantId);
        List<ChatMessage> messages = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
        assertThat(messages)
                .extracting(ChatMessage::role)
                .containsExactly(ChatRole.USER, ChatRole.ASSISTANT);
        AssistantTextPart assistantTextPart =
                (AssistantTextPart) messages.get(1).parts().parts().getFirst();
        assertThat(assistantTextPart.text()).isEqualTo("Đã thử lại thành công.");
    }

    @Test
    void terminal_stream_failure_is_forwarded_to_downstream_sink_without_retry() throws Exception {
        UUID tenantId = seedTenant();
        AtomicInteger modelCallCount = new AtomicInteger();
        RecordingChatStreamSink streamSink = new RecordingChatStreamSink();
        when(chatLlmGateway.streamChat(any(ChatStreamRequest.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            modelCallCount.incrementAndGet();
                            ChatStreamSink modelSink = invocation.getArgument(1);
                            // Non-transient code (e.g. a 4xx body validation failure from the
                            // upstream provider). MUST NOT auto-retry to avoid silent credit
                            // inflation or repeated bad-request shapes.
                            modelSink.emitError(
                                    "chat_stream_failed", "The assistant stream failed.");
                            return (Disposable) () -> {};
                        });

        withTenant(
                tenantId,
                () ->
                        chatOrchestrator.stream(
                                new ChatStreamCommand(
                                        tenantId.toString(),
                                        null,
                                        "Hãy thử với terminal failure",
                                        null),
                                streamSink));

        assertThat(streamSink.awaitFinish()).isTrue();
        assertThat(modelCallCount)
                .as("non-transient failures must NOT trigger a retry")
                .hasValue(1);
        assertThat(streamSink.events())
                .as("the terminal error MUST reach the downstream sink so the FE can show it")
                .anySatisfy(event -> assertThat(event).isEqualTo("error:chat_stream_failed"));
    }

    @Test
    void transient_failure_on_every_attempt_eventually_surfaces_as_error_to_downstream_sink()
            throws Exception {
        UUID tenantId = seedTenant();
        AtomicInteger modelCallCount = new AtomicInteger();
        RecordingChatStreamSink streamSink = new RecordingChatStreamSink();
        when(chatLlmGateway.streamChat(any(ChatStreamRequest.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            modelCallCount.incrementAndGet();
                            ChatStreamSink modelSink = invocation.getArgument(1);
                            // Both the initial call and the single retry hit the same transient
                            // path -- the orchestrator must give up after the retry budget and
                            // surface the (last) error to the downstream sink so the FE can show a
                            // toast or inline message.
                            modelSink.emitError(
                                    "chat_stream_transient",
                                    "The assistant stream was interrupted. Retrying.");
                            return (Disposable) () -> {};
                        });

        withTenant(
                tenantId,
                () ->
                        chatOrchestrator.stream(
                                new ChatStreamCommand(
                                        tenantId.toString(),
                                        null,
                                        "Hãy thử với mọi attempt đều transient",
                                        null),
                                streamSink));

        assertThat(streamSink.awaitFinish()).isTrue();
        assertThat(modelCallCount)
                .as(
                        "default budget = 1 retry, so transient on every attempt yields exactly 2 calls (initial + 1 retry)")
                .hasValue(2);
        assertThat(streamSink.events())
                .as("after the retry budget is exhausted the transient error must surface")
                .anySatisfy(event -> assertThat(event).isEqualTo("error:chat_stream_transient"));
    }

    @Test
    void transient_cancel_after_first_token_does_not_retry_to_avoid_duplicate_output()
            throws Exception {
        UUID tenantId = seedTenant();
        AtomicInteger modelCallCount = new AtomicInteger();
        RecordingChatStreamSink streamSink = new RecordingChatStreamSink();
        when(chatLlmGateway.streamChat(any(ChatStreamRequest.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            int callNumber = modelCallCount.incrementAndGet();
                            ChatStreamSink modelSink = invocation.getArgument(1);
                            // Simulate the gateway streaming a few tokens to the FE BEFORE the
                            // 9router HTTP/2 stream gets reset. Retrying would re-emit "Đây là "
                            // on top of the already-delivered tokens, producing a duplicated
                            // assistant turn on the user's screen. The orchestrator must instead
                            // forward the transient error so the FE can surface "stream
                            // interrupted, please retry" and the user keeps full context of what
                            // was partially shown.
                            modelSink.emitTextStart("assistant-text");
                            modelSink.emitTextDelta("assistant-text", "Đây là ");
                            modelSink.emitError(
                                    "chat_stream_transient",
                                    "The assistant stream was interrupted.");
                            return (Disposable) () -> {};
                        });

        withTenant(
                tenantId,
                () ->
                        chatOrchestrator.stream(
                                new ChatStreamCommand(
                                        tenantId.toString(),
                                        null,
                                        "Mid-response cancel must not retry",
                                        null),
                                streamSink));

        assertThat(streamSink.awaitFinish()).isTrue();
        assertThat(modelCallCount)
                .as(
                        "the orchestrator must NOT retry once output has been emitted, otherwise the FE would render duplicated tokens")
                .hasValue(1);
        assertThat(streamSink.events())
                .as("the partial tokens and the terminal error must both reach the downstream sink")
                .anySatisfy(event -> assertThat(event).isEqualTo("text-delta:Đây là "))
                .anySatisfy(event -> assertThat(event).isEqualTo("error:chat_stream_transient"));
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "orchestrator-transient-retry");
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
