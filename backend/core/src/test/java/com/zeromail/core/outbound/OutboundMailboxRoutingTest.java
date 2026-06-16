package com.zeromail.core.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RED contract for AUTO-06/AUD-02 outbound mailbox routing.
 *
 * <p>Waits on future field {@code OutboundSendCommand.mailboxRef} and audit columns {@code
 * source_mailbox_id}/{@code executing_mailbox_id}. The command field is reached through
 * record-component reflection, and the audit contract is reached through pg_indexes and
 * information_schema probes, so this compiles before the production shape exists.
 */
class OutboundMailboxRoutingTest extends PostgresContainerTest {

    private static final String OUTBOUND_SEND_COMMAND_FQN =
            "com.zeromail.core.outbound.usecases.OutboundSendCommand";
    private static final String MAILBOX_REF_FQN = "com.zeromail.core.mailbox.MailboxRef";

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void outboundSendCommandCarriesExecutingMailboxRef() throws Exception {
        Class<?> outboundSendCommandClass = Class.forName(OUTBOUND_SEND_COMMAND_FQN);

        RecordComponent mailboxRefComponent =
                Arrays.stream(outboundSendCommandClass.getRecordComponents())
                        .filter(recordComponent -> recordComponent.getName().equals("mailboxRef"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "OutboundSendCommand must carry mailboxRef so send/undo use the executing mailbox"));

        assertThat(mailboxRefComponent.getType().getName()).isEqualTo(MAILBOX_REF_FQN);
    }

    @Test
    void triageAuditRecordsSourceAndExecutingMailboxProvenance() {
        assertThat(columnNullability("triage_audit", "source_mailbox_id"))
                .as("triage_audit.source_mailbox_id must be backfilled and required")
                .containsExactly("NO");
        assertThat(columnNullability("triage_audit", "executing_mailbox_id"))
                .as("triage_audit.executing_mailbox_id must be backfilled and required")
                .containsExactly("NO");
    }

    @Test
    void triageAuditIdempotencyIncludesExecutingMailbox() {
        assertThat(indexDefinition("ux_triage_audit_idem"))
                .contains("tenant_id")
                .contains("executing_mailbox_id")
                .contains("gmail_message_id")
                .contains("rule_id")
                .contains("action_type")
                .contains("args_hash")
                .contains("NULLS NOT DISTINCT");
    }

    private List<String> columnNullability(String tableName, String columnName) {
        return jdbcTemplate.queryForList(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """,
                String.class,
                tableName,
                columnName);
    }

    private String indexDefinition(String indexName) {
        List<String> indexDefinitions =
                jdbcTemplate.queryForList(
                        """
                        SELECT indexdef
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND indexname = ?
                        """,
                        String.class,
                        indexName);
        assertThat(indexDefinitions).as(indexName + " must exist").hasSize(1);
        return indexDefinitions.getFirst();
    }
}
