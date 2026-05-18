package com.zeromail.core.chat.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.Disposable;

@SuppressWarnings("SqlResolve")
class ChatOrchestratorReadToolLoopIT extends PostgresContainerTest {

    @Autowired ChatOrchestrator chatOrchestrator;
    @Autowired ChatMessageJdbcRepository chatMessageRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ChatLlmGateway chatLlmGateway;
    @MockitoBean GmailApiClientFactory gmailApiClientFactory;

    @Test
    void read_tool_loop_executes_search_inbox_then_calls_model_again_with_tool_result()
            throws Exception {
        UUID tenantId = seedTenant();
        configureGmailSearch(tenantId);
        AtomicInteger modelCallCount = new AtomicInteger();
        AtomicReference<ChatStreamRequest> firstRequest = new AtomicReference<>();
        AtomicReference<ChatStreamRequest> secondRequest = new AtomicReference<>();
        RecordingChatStreamSink streamSink = new RecordingChatStreamSink();
        when(chatLlmGateway.streamChat(any(ChatStreamRequest.class), any(ChatStreamSink.class)))
                .thenAnswer(
                        invocation -> {
                            int callNumber = modelCallCount.incrementAndGet();
                            ChatStreamRequest streamRequest = invocation.getArgument(0);
                            ChatStreamSink modelSink = invocation.getArgument(1);
                            if (callNumber == 1) {
                                firstRequest.set(streamRequest);
                                modelSink.emitToolInputStart("tool-search-unread", "searchInbox");
                                modelSink.emitToolInputAvailable(
                                        "tool-search-unread",
                                        "searchInbox",
                                        "{\"query\":\"is:unread\",\"maxResults\":7}");
                                modelSink.emitFinish("stop");
                            } else {
                                secondRequest.set(streamRequest);
                                modelSink.emitTextStart("assistant-text");
                                modelSink.emitTextDelta("assistant-text", "Bạn có ");
                                modelSink.emitTextDelta("assistant-text", "7 email");
                                modelSink.emitTextDelta("assistant-text", " chưa đọc.");
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
                                        tenantId.toString(), null, "How many unread?", null),
                                streamSink));

        assertThat(streamSink.awaitFinish()).isTrue();
        assertThat(modelCallCount).hasValue(2);
        assertThat(firstRequest.get().conversationHistory()).hasSize(1);
        assertThat(secondRequest.get().conversationHistory())
                .anySatisfy(chatMessage -> assertThat(chatMessage.role()).isEqualTo(ChatRole.TOOL));

        UUID chatId =
                jdbcTemplate.queryForObject(
                        "select id from chat where tenant_id = ?", UUID.class, tenantId);
        List<ChatMessage> messages = chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
        assertThat(messages).hasSize(4);
        assertThat(messages)
                .extracting(ChatMessage::role)
                .containsExactly(
                        ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT);
        ToolOutputPart toolOutputPart = (ToolOutputPart) messages.get(2).parts().parts().getFirst();
        assertThat((List<?>) toolOutputPart.outputJson().get("messages")).hasSize(7);
        AssistantTextPart assistantTextPart =
                (AssistantTextPart) messages.get(3).parts().parts().getFirst();
        assertThat(assistantTextPart.text()).isEqualTo("Bạn có 7 email chưa đọc.");
        assertThat(streamSink.events())
                .containsSubsequence(
                        "tool-input-start:searchInbox",
                        "tool-input-available:searchInbox:{\"query\":\"is:unread\",\"maxResults\":7}",
                        "data-persistence:tool-call-saved",
                        "data-persistence:tool-output-saved",
                        "text-start:assistant-text",
                        "finish:complete");
        assertThat(streamSink.events())
                .anySatisfy(
                        event ->
                                assertThat(event)
                                        .startsWith("tool-output-available:")
                                        .contains("\"messageId\":\"unread-1\""));
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "orchestrator-read-loop");
        return tenantId;
    }

    private void configureGmailSearch(UUID tenantId) throws Exception {
        Gmail gmail = mock(Gmail.class);
        Gmail.Users users = mock(Gmail.Users.class);
        Gmail.Users.Messages messages = mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.List listRequest = mock(Gmail.Users.Messages.List.class);
        when(gmailApiClientFactory.buildClientForTenant(tenantId)).thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.list("me")).thenReturn(listRequest);
        when(listRequest.setQ(anyString())).thenReturn(listRequest);
        when(listRequest.setMaxResults(anyLong())).thenReturn(listRequest);
        List<Message> messageReferences = new ArrayList<>();
        for (int messageNumber = 1; messageNumber <= 7; messageNumber++) {
            String messageId = "unread-" + messageNumber;
            messageReferences.add(
                    new Message().setId(messageId).setThreadId("thread-" + messageNumber));
            Gmail.Users.Messages.Get getRequest = mock(Gmail.Users.Messages.Get.class);
            when(messages.get("me", messageId)).thenReturn(getRequest);
            when(getRequest.setFormat(anyString())).thenReturn(getRequest);
            when(getRequest.setFields(anyString())).thenReturn(getRequest);
            when(getRequest.setMetadataHeaders(anyList())).thenReturn(getRequest);
            when(getRequest.execute()).thenReturn(gmailMessage(messageId, messageNumber));
        }
        when(listRequest.execute())
                .thenReturn(new ListMessagesResponse().setMessages(messageReferences));
    }

    private static Message gmailMessage(String messageId, int messageNumber) {
        return new Message()
                .setId(messageId)
                .setThreadId("thread-" + messageNumber)
                .setSnippet("Unread snippet " + messageNumber)
                .setInternalDate(1_779_120_000_000L + messageNumber)
                .setPayload(
                        new MessagePart()
                                .setHeaders(
                                        List.of(
                                                header("From", "Sender " + messageNumber),
                                                header("To", "founder@example.test"),
                                                header("Subject", "Unread " + messageNumber))));
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    private static void withTenant(UUID tenantId, TenantRunnable tenantRunnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(tenantRunnable::run);
    }

    @FunctionalInterface
    private interface TenantRunnable {
        void run();
    }
}
