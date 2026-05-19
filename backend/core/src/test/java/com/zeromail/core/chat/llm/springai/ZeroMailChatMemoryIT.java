package com.zeromail.core.chat.llm.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class ZeroMailChatMemoryIT extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ChatMessageJdbcRepository chatMessageRepository;
    @Autowired ZeroMailChatMemory zeroMailChatMemory;

    @Test
    void reconstructs_history_and_tool_calls_from_chat_message_parts() {
        SeedData seedData = seedChat();
        chatMessageRepository.insert(
                new ChatMessage(
                        UUID.randomUUID(),
                        seedData.chatId(),
                        seedData.tenantId(),
                        "user",
                        ChatMessageParts.v1(List.of(new TextPart("text-1", "Find Acme"))),
                        Instant.now().minusSeconds(4)));
        chatMessageRepository.insert(
                new ChatMessage(
                        UUID.randomUUID(),
                        seedData.chatId(),
                        seedData.tenantId(),
                        "assistant",
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolCallPart(
                                                "tool-1",
                                                "call-1",
                                                "searchInbox",
                                                "input-available",
                                                Map.of("query", "Acme"),
                                                false))),
                        Instant.now().minusSeconds(3)));
        chatMessageRepository.insert(
                new ChatMessage(
                        UUID.randomUUID(),
                        seedData.chatId(),
                        seedData.tenantId(),
                        "tool",
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolOutputPart(
                                                "tool-output-1",
                                                "call-1",
                                                "searchInbox",
                                                "output-available",
                                                Map.of("messages", List.of("message-1")),
                                                false))),
                        Instant.now().minusSeconds(2)));
        chatMessageRepository.insert(
                new ChatMessage(
                        UUID.randomUUID(),
                        seedData.chatId(),
                        seedData.tenantId(),
                        "assistant",
                        ChatMessageParts.v1(
                                List.of(
                                        new AssistantTextPart(
                                                "assistant-text-1",
                                                "I found one message.",
                                                Instant.now()))),
                        Instant.now().minusSeconds(1)));

        List<Message> messages = zeroMailChatMemory.get(seedData.chatId().toString());

        assertThat(messages).hasSize(4);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        AssistantMessage assistantToolCallMessage = (AssistantMessage) messages.get(1);
        assertThat(assistantToolCallMessage.getToolCalls()).hasSize(1);
        assertThat(assistantToolCallMessage.getToolCalls().getFirst().name())
                .isEqualTo("searchInbox");
        assertThat(assistantToolCallMessage.getToolCalls().getFirst().arguments())
                .contains("\"query\":\"Acme\"");
        assertThat(messages.get(2)).isInstanceOf(ToolResponseMessage.class);
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) messages.get(2);
        assertThat(toolResponseMessage.getResponses()).hasSize(1);
        assertThat(toolResponseMessage.getResponses().getFirst().responseData())
                .contains("message-1");
    }

    @Test
    void token_budget_keeps_newest_turn_when_budget_is_tiny() {
        SeedData seedData = seedChat();
        chatMessageRepository.insert(
                new ChatMessage(
                        UUID.randomUUID(),
                        seedData.chatId(),
                        seedData.tenantId(),
                        "user",
                        ChatMessageParts.v1(List.of(new TextPart("text-1", "old message"))),
                        Instant.now().minusSeconds(2)));
        chatMessageRepository.insert(
                new ChatMessage(
                        UUID.randomUUID(),
                        seedData.chatId(),
                        seedData.tenantId(),
                        "assistant",
                        ChatMessageParts.v1(
                                List.of(
                                        new AssistantTextPart(
                                                "assistant-text-1",
                                                "newest message survives",
                                                Instant.now()))),
                        Instant.now().minusSeconds(1)));

        List<Message> messages = zeroMailChatMemory.get(seedData.chatId().toString(), 1);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getText()).contains("newest message survives");
    }

    private SeedData seedChat() {
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, "memory");
        jdbcTemplate.update(
                "insert into chat(id, tenant_id, title) values (?, ?, ?)",
                chatId,
                tenantId,
                "memory");
        return new SeedData(tenantId, chatId);
    }

    private record SeedData(UUID tenantId, UUID chatId) {}
}
