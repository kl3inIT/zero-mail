package com.zeromail.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RED contract for VER-03 mailbox-scope migrations.
 *
 * <p>Waits on Liquibase-applied 120..127 changesets and the backfilled mailbox columns they must
 * create. The compile-green mechanism is querying {@code DATABASECHANGELOG} and information_schema,
 * so no future entity or repository symbol is referenced before it exists. Runtime remains RED
 * until Plan 11-02 adds the append-only changesets.
 */
class Migration12xBackfillTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void migrationRange120Through127IsApplied() {
        List<String> appliedChangeSetIds =
                jdbcTemplate.queryForList(
                        """
                        SELECT id
                        FROM databasechangelog
                        WHERE id LIKE '12%'
                        ORDER BY id
                        """,
                        String.class);

        assertThat(appliedChangeSetIds).anyMatch(changeSetId -> changeSetId.startsWith("120-"));
        assertThat(appliedChangeSetIds).anyMatch(changeSetId -> changeSetId.startsWith("121-"));
        assertThat(appliedChangeSetIds).anyMatch(changeSetId -> changeSetId.startsWith("122-"));
        assertThat(appliedChangeSetIds).anyMatch(changeSetId -> changeSetId.startsWith("123-"));
        assertThat(appliedChangeSetIds).anyMatch(changeSetId -> changeSetId.startsWith("124-"));
        assertThat(appliedChangeSetIds).anyMatch(changeSetId -> changeSetId.startsWith("125-"));
        assertThat(appliedChangeSetIds).anyMatch(changeSetId -> changeSetId.startsWith("126-"));
        assertThat(appliedChangeSetIds).anyMatch(changeSetId -> changeSetId.startsWith("127-"));
    }

    @Test
    void mailboxScopeColumnsExistAndAreNotNullableAfterBackfill() {
        List<ColumnExpectation> requiredColumns =
                List.of(
                        new ColumnExpectation("pubsub_delivery", "gmail_connection_id"),
                        new ColumnExpectation("mail_message_observed", "gmail_connection_id"),
                        new ColumnExpectation("gmail_inbox_projection", "gmail_connection_id"),
                        new ColumnExpectation("gmail_inbox_sync_state", "gmail_connection_id"),
                        // processing_job.gmail_connection_id is intentionally NULLABLE (changeset
                        // 128): it also holds tenant-scoped and admin-global jobs with no mailbox.
                        new ColumnExpectation("triage_audit", "source_mailbox_id"),
                        new ColumnExpectation("triage_audit", "executing_mailbox_id"),
                        new ColumnExpectation("rules", "gmail_connection_id"));

        for (ColumnExpectation requiredColumn : requiredColumns) {
            assertThat(columnNullability(requiredColumn.tableName(), requiredColumn.columnName()))
                    .as(requiredColumn.tableName() + "." + requiredColumn.columnName())
                    .containsExactly("NO");
        }
    }

    @Test
    void mailboxOwnedIndexesReplaceTenantOnlyUniqueness() {
        assertThat(indexDefinition("ux_triage_audit_idem"))
                .contains("executing_mailbox_id")
                .contains("NULLS NOT DISTINCT");
        assertThat(indexDefinition("uq_rules_tenant_template_key_present"))
                .contains("gmail_connection_id")
                .contains("template_key");
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

    private record ColumnExpectation(String tableName, String columnName) {}
}
