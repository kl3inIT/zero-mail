package com.zeromail.core.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.migration.OldTwoMailboxFixture;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RED contract for ING-03/ING-06.
 *
 * <p>Waits on schema column {@code mail_message_observed.gmail_connection_id} and primary key
 * {@code (tenant_id, gmail_connection_id, gmail_message_id)}. The test uses information_schema
 * probes plus raw JDBC inserts, so it compiles today and fails at assertion time until the schema
 * migration lands. Raw JDBC is the compile-green DB round-trip mechanism; no Hibernate L1 cache can
 * hide the same-message-id collision.
 */
class ObservedMailboxPkTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void mailMessageObservedPrimaryKeyIncludesMailbox() {
        assertThat(primaryKeyColumns("mail_message_observed"))
                .containsExactly("tenant_id", "gmail_connection_id", "gmail_message_id");
    }

    @Test
    void sameGmailMessageIdCanBeObservedInTwoMailboxes() {
        assertThat(columnExists("mail_message_observed", "gmail_connection_id"))
                .as(
                        "mail_message_observed must carry gmail_connection_id before collision tests run")
                .isTrue();

        OldTwoMailboxFixture.SeededMailboxes seededMailboxes =
                new OldTwoMailboxFixture(jdbcTemplate).seedConnectedMailboxes("observed-pk");
        String gmailMessageId = "same-message-id-across-mailboxes";

        insertObserved(seededMailboxes, seededMailboxes.primaryGmailConnectionId(), gmailMessageId);
        insertObserved(
                seededMailboxes, seededMailboxes.secondaryGmailConnectionId(), gmailMessageId);

        Integer observedRowCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM mail_message_observed
                        WHERE tenant_id = ? AND gmail_message_id = ?
                        """,
                        Integer.class,
                        seededMailboxes.tenantId(),
                        gmailMessageId);

        assertThat(observedRowCount).isEqualTo(2);
    }

    private void insertObserved(
            OldTwoMailboxFixture.SeededMailboxes seededMailboxes,
            java.util.UUID gmailConnectionId,
            String gmailMessageId) {
        jdbcTemplate.update(
                """
                INSERT INTO mail_message_observed(
                    tenant_id, gmail_connection_id, gmail_message_id, gmail_thread_id,
                    history_id, label_ids, observed_at
                ) VALUES (?, ?, ?, ?, ?, ARRAY['INBOX']::text[], NOW())
                """,
                seededMailboxes.tenantId(),
                gmailConnectionId,
                gmailMessageId,
                "thread-" + gmailConnectionId,
                100L);
    }

    private List<String> primaryKeyColumns(String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT key_column_usage.column_name
                FROM information_schema.table_constraints table_constraints
                JOIN information_schema.key_column_usage key_column_usage
                  ON key_column_usage.constraint_name = table_constraints.constraint_name
                 AND key_column_usage.table_schema = table_constraints.table_schema
                 AND key_column_usage.table_name = table_constraints.table_name
                WHERE table_constraints.table_schema = 'public'
                  AND table_constraints.table_name = ?
                  AND table_constraints.constraint_type = 'PRIMARY KEY'
                ORDER BY key_column_usage.ordinal_position
                """,
                String.class,
                tableName);
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer columnCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND column_name = ?
                        """,
                        Integer.class,
                        tableName,
                        columnName);
        return columnCount != null && columnCount > 0;
    }
}
