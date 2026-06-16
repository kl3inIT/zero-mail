package com.zeromail.core.chat.confirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.chat.usecases.ConfirmActionService;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.mailbox.MailboxContext;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.usecases.SenderSafetyNetService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@SuppressWarnings("SqlResolve")
class ConfirmationRaceIT extends PostgresContainerTest {

    @Autowired ConfirmActionService confirmActionService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean GmailApiClientFactory gmailApiClientFactory;
    @MockitoBean ConfirmationLeaseService confirmationLeaseService;
    @MockitoBean SenderSafetyNetService senderSafetyNetService;

    @Test
    void double_click_race_commits_exactly_one_send() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID gmailConnectionId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        String toolCallId = "tool-send-race";
        seedTenantAndChat(tenantId, chatId, "confirmation-race");
        seedPendingSend(tenantId, chatId, toolCallId, "founder@example.test");
        AtomicInteger gmailSendCount = new AtomicInteger();
        configureGmail(tenantId, gmailConnectionId, gmailSendCount);
        when(confirmationLeaseService.tryAcquire(eq(chatId), eq(toolCallId), anyString()))
                .thenReturn(true);
        when(senderSafetyNetService.isProtected(any(UUID.class), anyString())).thenReturn(false);

        CompletableFuture<Boolean> firstConfirm =
                CompletableFuture.supplyAsync(
                        () -> confirm(tenantId, gmailConnectionId, chatId, toolCallId));
        CompletableFuture<Boolean> secondConfirm =
                CompletableFuture.supplyAsync(
                        () -> confirm(tenantId, gmailConnectionId, chatId, toolCallId));
        CompletableFuture.allOf(firstConfirm, secondConfirm).join();

        assertThat(List.of(firstConfirm.get(), secondConfirm.get()))
                .containsExactlyInAnyOrder(true, false);
        assertThat(gmailSendCount).hasValue(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select count(*)
                                  from assistant_action_audit
                                 where tenant_id = ?
                                   and chat_id = ?
                                   and tool_call_id = ?
                                   and state = 'COMMITTED'
                                """,
                                Integer.class,
                                tenantId,
                                chatId,
                                toolCallId))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                select state
                                  from assistant_pending_action
                                 where tenant_id = ?
                                   and chat_id = ?
                                   and tool_call_id = ?
                                """,
                                String.class,
                                tenantId,
                                chatId,
                                toolCallId))
                .isEqualTo("CONFIRMED");
    }

    private boolean confirm(UUID tenantId, UUID gmailConnectionId, UUID chatId, String toolCallId) {
        try {
            ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                    .run(
                            () ->
                                    ScopedValue.where(MailboxContext.MAILBOX, gmailConnectionId)
                                            .run(
                                                    () ->
                                                            confirmActionService.confirm(
                                                                    chatId,
                                                                    toolCallId,
                                                                    true,
                                                                    Map.of())));
            return true;
        } catch (RuntimeException confirmationFailure) {
            return false;
        }
    }

    private void configureGmail(UUID tenantId, UUID gmailConnectionId, AtomicInteger gmailSendCount)
            throws Exception {
        Gmail gmail = org.mockito.Mockito.mock(Gmail.class);
        Gmail.Users users = org.mockito.Mockito.mock(Gmail.Users.class);
        Gmail.Users.Messages messages = org.mockito.Mockito.mock(Gmail.Users.Messages.class);
        Gmail.Users.Messages.Send sendRequest =
                org.mockito.Mockito.mock(Gmail.Users.Messages.Send.class);
        when(gmailApiClientFactory.buildClientForMailbox(
                        new MailboxRef(tenantId, gmailConnectionId)))
                .thenReturn(gmail);
        when(gmail.users()).thenReturn(users);
        when(users.messages()).thenReturn(messages);
        when(messages.send(eq("me"), any(Message.class))).thenReturn(sendRequest);
        when(sendRequest.execute())
                .thenAnswer(
                        _ ->
                                new Message()
                                        .setId("gmail-race-" + gmailSendCount.incrementAndGet())
                                        .setThreadId("thread-race"));
    }

    private void seedTenantAndChat(UUID tenantId, UUID chatId, String title) {
        jdbcTemplate.update("insert into tenants(id, display_name) values (?, ?)", tenantId, title);
        jdbcTemplate.update(
                "insert into chat(id, tenant_id, title) values (?, ?, ?)", chatId, tenantId, title);
    }

    private void seedPendingSend(UUID tenantId, UUID chatId, String toolCallId, String recipient)
            throws Exception {
        UUID chatMessageId = UUID.randomUUID();
        Map<String, Object> parts =
                Map.of(
                        "schemaVersion",
                        1,
                        "parts",
                        List.of(
                                Map.of(
                                        "type",
                                        "tool-sendEmail",
                                        "toolCallId",
                                        toolCallId,
                                        "state",
                                        "input-available",
                                        "input",
                                        Map.of(
                                                "to",
                                                recipient,
                                                "subject",
                                                "Race",
                                                "body",
                                                "User-authored race body"))));
        jdbcTemplate.update(
                """
                insert into chat_message(id, chat_id, tenant_id, role, parts, created_at)
                values (?, ?, ?, 'assistant', ?::jsonb, ?)
                """,
                chatMessageId,
                chatId,
                tenantId,
                objectMapper.writeValueAsString(parts),
                Timestamp.from(Instant.now()));
        jdbcTemplate.update(
                """
                insert into assistant_pending_action(
                  id, chat_id, tenant_id, chat_message_id, tool_call_id, state, draft_body,
                  expires_at, created_at, updated_at, version
                )
                values (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, 0)
                """,
                UUID.randomUUID(),
                chatId,
                tenantId,
                chatMessageId,
                toolCallId,
                "User-authored race body",
                Timestamp.from(Instant.now().plusSeconds(1800)),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }
}
