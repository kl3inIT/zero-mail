package com.zeromail.core.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminSchemaMigrationTest extends PostgresContainerTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void admin_tables_and_chain_columns_are_created_by_liquibase() {
        assertThat(tableExists("admin_users")).isTrue();
        assertThat(tableExists("admin_audit_event")).isTrue();
        assertThat(tableExists("admin_read_event")).isTrue();
        assertThat(columnExists("admin_audit_event", "chain_index")).isTrue();
        assertThat(columnExists("admin_audit_event", "canonical_timestamp_ms")).isTrue();
    }

    @Test
    void admin_user_status_constraint_rejects_unknown_status() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000811");

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO admin_users (id, email, user_handle, status)
                                        VALUES (?, ?, ?, ?)
                                        """,
                                        adminUserId,
                                        "invalid-status@example.com",
                                        new byte[] {0x11},
                                        "BROKEN"))
                .hasMessageContaining("ck_admin_users_status");
    }

    @Test
    void admin_audit_event_trigger_rejects_update() {
        UUID adminUserId = UUID.fromString("00000000-0000-4000-8000-000000000812");
        UUID auditEventId = UUID.fromString("00000000-0000-4000-8000-000000000813");

        jdbcTemplate.update(
                """
                INSERT INTO admin_users (id, email, user_handle, status)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                adminUserId,
                "trigger-check@example.com",
                new byte[] {0x12},
                "ACTIVE");
        jdbcTemplate.update(
                """
                INSERT INTO admin_audit_event (
                    id, actor_user_id, actor_email, action, reason, canonical_timestamp_ms,
                    hmac_chain_hash
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                auditEventId,
                adminUserId,
                "trigger-check@example.com",
                "ADMIN_SCHEMA_TEST",
                "initial reason",
                1_779_212_177_000L,
                new byte[32]);

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE admin_audit_event SET reason = ? WHERE id = ?",
                                        "tampered",
                                        auditEventId))
                .hasMessageContaining("admin_audit_event is append-only");
    }

    private boolean tableExists(String tableName) {
        Integer tableCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """,
                        Integer.class,
                        tableName);
        return tableCount != null && tableCount == 1;
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
        return columnCount != null && columnCount == 1;
    }
}
