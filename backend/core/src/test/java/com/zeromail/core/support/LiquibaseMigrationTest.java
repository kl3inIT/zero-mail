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
 * [BLOCKING] schema-push proof: boots Spring against a real Postgres 17 Testcontainer,
 * Liquibase applies all changesets, then asserts every required table is present.
 */
class LiquibaseMigrationTest extends PostgresContainerTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void all_tables_exist() throws Exception {
        Set<String> seen = new HashSet<>();
        try (var c = dataSource.getConnection();
             var md = c.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (md.next()) {
                seen.add(md.getString("TABLE_NAME"));
            }
        }
        assertThat(seen).contains(
                "tenants",
                "users",
                "gmail_connections",
                "onboarding_selections",
                "event_publication",
                "triage_audit",
                "tenant_sender_opt_in",
                "tenant_protected_sender_observation");
    }

    @Test
    void triage_audit_null_rule_id_idempotency_index_treats_nulls_as_not_distinct() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("insert into tenants(id, display_name) values (?, ?)", tenantId, "triage-schema");

        byte[] argsHash = new byte[32];
        insertPendingAuditRow(tenantId, argsHash);

        assertThat(indexDefinition("ux_triage_audit_idem")).contains("NULLS NOT DISTINCT");
        assertThatThrownBy(() -> insertPendingAuditRow(tenantId, argsHash))
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
}
