package com.zeromail.worker.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.worker.PostgresContainerTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminReadEventPurgeJobTest extends PostgresContainerTest {

    @Autowired private AdminReadEventPurgeJob adminReadEventPurgeJob;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAdminReadEvents() {
        jdbcTemplate.execute("DELETE FROM admin_read_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event DISABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM admin_audit_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event ENABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM admin_users");
    }

    @Test
    void purge_deletes_read_events_older_than_thirty_days() {
        UUID adminUserId = seedAdminUser();
        UUID oldReadEventId = seedReadEvent(adminUserId, Instant.now().minusSeconds(31L * 86_400L));
        UUID freshReadEventId =
                seedReadEvent(adminUserId, Instant.now().minusSeconds(10L * 86_400L));

        int deletedRows = adminReadEventPurgeJob.purgeOnce();

        assertThat(deletedRows).isEqualTo(1);
        assertThat(readEventExists(oldReadEventId)).isFalse();
        assertThat(readEventExists(freshReadEventId)).isTrue();
    }

    private UUID seedAdminUser() {
        UUID adminUserId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO admin_users(id, email, user_handle, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
                adminUserId,
                "admin-read-purge@example.com",
                new byte[] {0x73});
        return adminUserId;
    }

    private UUID seedReadEvent(UUID adminUserId, Instant createdAt) {
        UUID readEventId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO admin_read_event(
                    id, actor_user_id, actor_email, action, target_kind, target_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                readEventId,
                adminUserId,
                "admin-read-purge@example.com",
                "TENANT_DETAIL_VIEWED",
                "TENANT",
                UUID.randomUUID(),
                Timestamp.from(createdAt));
        return readEventId;
    }

    private boolean readEventExists(UUID readEventId) {
        Long rowCount =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM admin_read_event WHERE id = ?",
                        Long.class,
                        readEventId);
        return rowCount != null && rowCount > 0;
    }
}
