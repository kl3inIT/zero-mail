package com.zeromail.core.chat.confirm;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.chat.confirm.ConfirmationStateMachine.PendingAction;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.SendCommitCommand;
import com.zeromail.core.chat.confirm.ConfirmationStateMachine.SendInFlightCommand;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.support.PostgresContainerTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@SuppressWarnings("SqlResolve")
class AuditAtomicityIT extends PostgresContainerTest {

    @Autowired ConfirmationStateMachine confirmationStateMachine;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void concurrent_commits_leave_audit_rows_and_pending_actions_atomically_confirmed()
            throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        seedTenantAndChat(tenantId, chatId, "audit-atomicity");
        List<String> toolCallIds = new ArrayList<>();
        for (int actionIndex = 0; actionIndex < 100; actionIndex++) {
            String toolCallId = "tool-send-atomic-" + actionIndex;
            toolCallIds.add(toolCallId);
            seedPendingSend(tenantId, chatId, toolCallId, "recipient-" + actionIndex + "@test.tld");
        }

        try (java.util.concurrent.ExecutorService executorService =
                Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> confirmations =
                    toolCallIds.stream()
                            .<Callable<Void>>map(
                                    toolCallId ->
                                            () -> {
                                                PendingAction pendingAction =
                                                        confirmationStateMachine.loadPendingAction(
                                                                chatId, tenantId, toolCallId);
                                                confirmationStateMachine.reserve(
                                                        chatId,
                                                        tenantId,
                                                        toolCallId,
                                                        pendingAction.partsUpdatedAt());
                                                UUID auditId =
                                                        confirmationStateMachine.recordSendInFlight(
                                                                new SendInFlightCommand(
                                                                        tenantId,
                                                                        chatId,
                                                                        toolCallId,
                                                                        ChatToolName.SEND_EMAIL,
                                                                        "<"
                                                                                + tenantId
                                                                                + "."
                                                                                + chatId
                                                                                + "."
                                                                                + toolCallId
                                                                                + "@zero-mail.invalid>",
                                                                        "recipient-hash",
                                                                        "subject-hash",
                                                                        "{\"state\":\"send_in_flight\"}",
                                                                        "{\"state\":\"preview\"}"));
                                                confirmationStateMachine.commitSendCompleted(
                                                        auditId,
                                                        new SendCommitCommand(
                                                                tenantId,
                                                                chatId,
                                                                toolCallId,
                                                                "{\"state\":\"committed\"}"));
                                                return null;
                                            })
                            .toList();
            for (java.util.concurrent.Future<Void> future :
                    executorService.invokeAll(confirmations)) {
                future.get();
            }
        }

        assertThat(count("assistant_action_audit", tenantId, "state = 'COMMITTED'")).isEqualTo(100);
        assertThat(count("assistant_action_audit", tenantId, "state = 'SEND_IN_FLIGHT'")).isZero();
        assertThat(count("assistant_pending_action", tenantId, "state = 'CONFIRMED'"))
                .isEqualTo(100);
        assertThat(
                        count(
                                "assistant_pending_action",
                                tenantId,
                                "state in ('PENDING', 'PROCESSING')"))
                .isZero();
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
                                                "Atomicity",
                                                "body",
                                                "User-authored body"))));
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
                "User-authored body",
                Timestamp.from(Instant.now().plusSeconds(1800)),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
    }

    private int count(String tableName, UUID tenantId, String predicate) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where tenant_id = ? and " + predicate,
                Integer.class,
                tenantId);
    }
}
