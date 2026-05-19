package com.zeromail.core.chat.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.chat.usecases.ChatHistoryService;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class ChatHistoryProjectorIT extends PostgresContainerTest {

    @Autowired ChatHistoryProjector chatHistoryProjector;
    @Autowired ChatHistoryService chatHistoryService;
    @Autowired ChatMessageJdbcRepository chatMessageRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void projects_ordered_messages_and_hides_soft_deleted_chats_from_history() {
        UUID tenantId = seedTenant();
        UUID firstChatId = seedChat(tenantId, "First chat", Instant.parse("2026-05-18T00:00:00Z"));
        UUID deletedChatId =
                seedChat(tenantId, "Deleted chat", Instant.parse("2026-05-18T00:01:00Z"));
        UUID newestChatId =
                seedChat(tenantId, "Newest chat", Instant.parse("2026-05-18T00:02:00Z"));
        seedMixedMessages(firstChatId, tenantId);
        seedMixedMessages(deletedChatId, tenantId);
        seedMixedMessages(newestChatId, tenantId);

        withTenant(tenantId, () -> chatHistoryService.softDelete(deletedChatId));

        ChatHistoryDetail detail = chatHistoryProjector.project(tenantId, firstChatId);
        assertThat(detail.messages()).hasSize(4);
        assertThat(detail.messages())
                .extracting(ChatMessageProjection::role)
                .containsExactly(
                        ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL, ChatRole.ASSISTANT);
        assertThat(detail.messages().get(1).parts().parts().getFirst())
                .isInstanceOf(ToolCallPart.class);
        assertThat(detail.messages().get(2).parts().parts().getFirst())
                .isInstanceOf(ToolOutputPart.class);

        List<ChatHistoryProjection> summaries =
                withTenant(tenantId, () -> chatHistoryService.listForCurrentTenant(50, 0));
        assertThat(summaries)
                .extracting(ChatHistoryProjection::id)
                .containsExactly(newestChatId, firstChatId);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "history-projector");
        return tenantId;
    }

    private UUID seedChat(UUID tenantId, String title, Instant updatedAt) {
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into chat(id, tenant_id, title, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 0)
                """,
                chatId,
                tenantId,
                title,
                Timestamp.from(updatedAt.minusSeconds(30)),
                Timestamp.from(updatedAt));
        return chatId;
    }

    private void seedMixedMessages(UUID chatId, UUID tenantId) {
        Instant baseTime = Instant.parse("2026-05-18T00:03:00Z");
        chatMessageRepository.insert(
                message(
                        chatId,
                        tenantId,
                        ChatRole.USER,
                        ChatMessageParts.v1(List.of(new TextPart("text-user", "hello"))),
                        baseTime));
        chatMessageRepository.insert(
                message(
                        chatId,
                        tenantId,
                        ChatRole.ASSISTANT,
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolCallPart(
                                                "tool-call",
                                                "tool-call-1",
                                                "searchInbox",
                                                "input-available",
                                                Map.of("query", "is:unread"),
                                                false))),
                        baseTime.plusSeconds(1)));
        chatMessageRepository.insert(
                message(
                        chatId,
                        tenantId,
                        ChatRole.TOOL,
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolOutputPart(
                                                "tool-output",
                                                "tool-call-1",
                                                "searchInbox",
                                                "output-available",
                                                Map.of(
                                                        "messages",
                                                        List.of(Map.of("messageId", "m1"))),
                                                false))),
                        baseTime.plusSeconds(2)));
        chatMessageRepository.insert(
                message(
                        chatId,
                        tenantId,
                        ChatRole.ASSISTANT,
                        ChatMessageParts.v1(
                                List.of(new AssistantTextPart("assistant", "done", baseTime))),
                        baseTime.plusSeconds(3)));
    }

    private static ChatMessage message(
            UUID chatId,
            UUID tenantId,
            ChatRole chatRole,
            ChatMessageParts parts,
            Instant createdAt) {
        return new ChatMessage(
                UUID.randomUUID(), chatId, tenantId, chatRole.id(), parts, createdAt);
    }

    private static <T> T withTenant(UUID tenantId, TenantCallable<T> tenantCallable) {
        try {
            return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                    .call(tenantCallable::call);
        } catch (Exception exception) {
            throw new IllegalStateException("tenant-scoped test action failed", exception);
        }
    }

    private static void withTenant(UUID tenantId, TenantRunnable tenantRunnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(tenantRunnable::run);
    }

    @FunctionalInterface
    private interface TenantCallable<T> {
        T call() throws Exception;
    }

    @FunctionalInterface
    private interface TenantRunnable {
        void run();
    }
}
