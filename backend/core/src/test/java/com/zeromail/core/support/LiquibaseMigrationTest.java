package com.zeromail.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * [BLOCKING] schema-push proof: boots Spring against a real Postgres 17 Testcontainer, Liquibase
 * applies all changesets, then asserts every required table is present.
 */
@SuppressWarnings({"SqlResolve", "SameParameterValue"})
class LiquibaseMigrationTest extends PostgresContainerTest {

    @Autowired DataSource dataSource;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void all_tables_exist() throws Exception {
        Set<String> seen = new HashSet<>();
        try (var c = dataSource.getConnection();
                var md = c.getMetaData().getTables(null, "public", "%", new String[] {"TABLE"})) {
            while (md.next()) {
                seen.add(md.getString("TABLE_NAME"));
            }
        }
        assertThat(seen)
                .contains(
                        "tenants",
                        "users",
                        "gmail_connections",
                        "onboarding_selections",
                        "event_publication",
                        "triage_audit",
                        "tenant_sender_opt_in",
                        "tenant_protected_sender_observation",
                        "thread_reply_status",
                        "chat",
                        "chat_message",
                        "assistant_pending_action",
                        "assistant_action_audit",
                        "assistant_settings",
                        "assistant_memory",
                        "assistant_knowledge_snippet");
    }

    @Test
    void triage_audit_null_rule_id_idempotency_index_treats_nulls_as_not_distinct() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, "triage-schema");

        byte[] argsHash = new byte[32];
        insertPendingAuditRow(tenantId, argsHash);

        assertThat(indexDefinition("ux_triage_audit_idem")).contains("NULLS NOT DISTINCT");
        assertThatThrownBy(() -> insertPendingAuditRow(tenantId, argsHash))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void thread_reply_status_indexes_and_fk_cascade_exist() {
        assertThat(indexDefinition("ux_thread_reply_status_tenant_thread"))
                .contains("(tenant_id, gmail_thread_id)");
        assertThat(indexDefinition("idx_thread_reply_status_inbox"))
                .contains(
                        "tenant_id",
                        "bucket",
                        "resolved",
                        "last_classified_at DESC NULLS LAST",
                        "gmail_thread_id DESC");
        assertThat(indexDefinition("idx_thread_reply_status_resolved"))
                .contains(
                        "tenant_id",
                        "resolved",
                        "last_classified_at DESC NULLS LAST",
                        "gmail_thread_id DESC");
        assertThat(indexDefinition("idx_thread_reply_status_to_reply"))
                .contains("WHERE", "'TO_REPLY'::text", "NOT resolved");
        assertThat(constraintDefinition("fk_thread_reply_status_tenant"))
                .contains("FOREIGN KEY (tenant_id)")
                .contains("REFERENCES tenants(id)")
                .contains("ON DELETE CASCADE");
    }

    @Test
    void chat_schema_owns_body_ban_trigger_and_pending_action_cas_columns() {
        assertThat(columnExists("chat_message", "updated_at")).isFalse();
        assertThat(columnExists("assistant_pending_action", "parts_updated_at")).isTrue();
        assertThat(columnExists("assistant_pending_action", "draft_body")).isTrue();
        assertThat(columnExists("assistant_action_audit", "tool_category")).isTrue();
        assertThat(columnExists("assistant_action_audit", "state")).isTrue();
        assertThat(columnExists("assistant_action_audit", "in_flight_at")).isTrue();
        assertThat(columnExists("assistant_settings", "assistant_settings_id")).isTrue();
        assertThat(columnExists("assistant_settings", "version")).isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from pg_trigger where tgname = 'chat_message_body_ban'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void assistant_action_audit_state_check_allows_failed_without_sent_at_only() {
        UUID tenantId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        UUID chatMessageId = UUID.randomUUID();

        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, "chat-schema");
        jdbcTemplate.update(
                "insert into chat(id, tenant_id, title) values (?, ?, ?)",
                chatId,
                tenantId,
                "Schema check");
        jdbcTemplate.update(
                """
                insert into chat_message(id, chat_id, tenant_id, role, parts)
                values (?, ?, ?, 'assistant', '{"schemaVersion":1,"parts":[{"type":"text","text":"hi"}]}'::jsonb)
                """,
                chatMessageId,
                chatId,
                tenantId);

        jdbcTemplate.update(
                """
                insert into assistant_action_audit(
                  id, chat_id, tenant_id, tool_call_id, tool_category, tool_name, state,
                  preview_snapshot
                )
                values (?, ?, ?, 'tool-failed', 'confirmed-send', 'sendEmail', 'FAILED', '{}'::jsonb)
                """,
                UUID.randomUUID(),
                chatId,
                tenantId);

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        insert into assistant_action_audit(
                                          id, chat_id, tenant_id, tool_call_id, tool_category, tool_name, state,
                                          preview_snapshot
                                        )
                                        values (?, ?, ?, 'tool-committed', 'confirmed-send', 'sendEmail',
                                          'COMMITTED', '{}'::jsonb)
                                        """,
                                        UUID.randomUUID(),
                                        chatId,
                                        tenantId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertPendingAuditRow(UUID tenantId, byte[] argsHash) {
        jdbcTemplate.update(
                """
                insert into triage_audit(
                  tenant_id, gmail_message_id, rule_id, action_type, args_hash,
                  action_args_json, decision, reason
                )
                values (?, 'gmail-message-1', null, 'archive', ?, '{"type":"archive"}'::jsonb,
                  'PENDING', 'matcher-node-1')
                """,
                tenantId,
                argsHash);
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = ?", String.class, indexName);
    }

    private String constraintDefinition(String constraintName) {
        return jdbcTemplate.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint where conname = ?",
                String.class,
                constraintName);
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = ?
                          and column_name = ?
                        """,
                        Integer.class,
                        tableName,
                        columnName);
        return count != null && count > 0;
    }
}
