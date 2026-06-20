package com.zeromail.core.calendar.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.support.PostgresContainerTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Schema-invariant gate for the Phase 12 Calendar foundation (CAL-CONN-06). Boots a real Postgres
 * 18 Testcontainer, lets Liquibase apply every changeset including 131-134, then asserts:
 *
 * <ul>
 *   <li>{@code calendar_connections} table exists.
 *   <li>{@code calendar_connections} has NO {@code gmail_connection_id} column — workspace-shared,
 *       NOT mailbox-isolated; this prevents a join-based design from sneaking in at the schema
 *       layer before any W2 service code is written.
 *   <li>{@code calendar_connections} carries the columns W1/W2 require: {@code tenant_id}, {@code
 *       google_email}, {@code status}, {@code refresh_token_encrypted}.
 *   <li>{@code mailbox_calendar_preferences} has the per-role partial unique indexes ({@code
 *       uq_mailbox_event_write}, {@code uq_mailbox_brief_source}) — multi-select FREEBUSY +
 *       single-select EVENT_WRITE / BRIEF_SOURCE per the Q3 lock.
 *   <li>{@code gmail_inbox_projection} has the two new W4 columns ({@code message_class}, {@code
 *       event_dt}).
 * </ul>
 *
 * Privacy note: assertions are metadata-only (column / index names). No row content is read; no
 * {@code google_email} value ever touches the test output.
 */
@Tag("schema")
class CalendarSchemaIsolationTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void calendar_connections_has_no_gmail_connection_id_column() {
        Integer columnPresenceCount =
                jdbcTemplate.queryForObject(
                        """
                        select count(*)
                          from information_schema.columns
                         where table_schema = 'public'
                           and table_name = 'calendar_connections'
                           and column_name = 'gmail_connection_id'
                        """,
                        Integer.class);

        assertThat(columnPresenceCount)
                .as(
                        "CAL-CONN-06 invariant: calendar_connections must be workspace-shared "
                                + "and carry NO gmail_connection_id column")
                .isEqualTo(0);
    }

    @Test
    void calendar_connections_carries_the_expected_workspace_shared_columns() {
        assertThat(columnExists("calendar_connections", "tenant_id")).isTrue();
        assertThat(columnExists("calendar_connections", "google_email")).isTrue();
        assertThat(columnExists("calendar_connections", "status")).isTrue();
        assertThat(columnExists("calendar_connections", "refresh_token_encrypted")).isTrue();
        assertThat(columnExists("calendar_connections", "scopes_granted")).isTrue();
        assertThat(columnExists("calendar_connections", "connected_at")).isTrue();
        assertThat(columnExists("calendar_connections", "disconnected_at")).isTrue();
    }

    @Test
    void calendars_table_exists_with_workspace_isolation_columns() {
        assertThat(columnExists("calendars", "calendar_connection_id")).isTrue();
        assertThat(columnExists("calendars", "external_calendar_id")).isTrue();
        assertThat(columnExists("calendars", "is_primary")).isTrue();
        assertThat(columnExists("calendars", "is_enabled")).isTrue();
    }

    @Test
    void mailbox_calendar_preferences_has_per_role_partial_unique_indexes() {
        String eventWriteIndex = indexDefinition("uq_mailbox_event_write");
        String briefSourceIndex = indexDefinition("uq_mailbox_brief_source");

        assertThat(eventWriteIndex)
                .as("EVENT_WRITE single-select per mailbox enforced by partial unique index")
                .contains("(mailbox_id)")
                .contains("EVENT_WRITE");
        assertThat(briefSourceIndex)
                .as("BRIEF_SOURCE single-select per mailbox enforced by partial unique index")
                .contains("(mailbox_id)")
                .contains("BRIEF_SOURCE");
    }

    @Test
    void gmail_inbox_projection_has_the_two_phase_12_calendar_columns() {
        assertThat(columnExists("gmail_inbox_projection", "message_class")).isTrue();
        assertThat(columnExists("gmail_inbox_projection", "event_dt")).isTrue();
    }

    @Test
    void inbox_projection_calendar_pin_partial_index_exists() {
        String pinIndex = indexDefinition("idx_inbox_projection_calendar_pin");

        assertThat(pinIndex)
                .as("W4 read-side ORDER BY needs the calendar-message partial index")
                .contains("message_class IS NOT NULL");
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

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = ?", String.class, indexName);
    }
}
