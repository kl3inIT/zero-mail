package com.zeromail.core.admin.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.admin.queue.usecases.QueueJobActionService;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class QueueJobActionServiceTest extends PostgresContainerTest {

    private static final UUID ADMIN_USER_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000008e2");
    private static final AdminUser ADMIN_USER =
            new AdminUser(
                    ADMIN_USER_ID,
                    "queue-action-admin@example.com",
                    AdminStatus.ACTIVE,
                    Optional.of("Queue Action Admin"));

    @Autowired private QueueJobActionService queueJobActionService;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM admin_read_event WHERE actor_user_id = ?", ADMIN_USER_ID);
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event DISABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.update("DELETE FROM admin_audit_event WHERE actor_user_id = ?", ADMIN_USER_ID);
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event ENABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("TRUNCATE TABLE processing_job CASCADE");
        adminUserRepository.save(
                new AdminUserEntity(
                        ADMIN_USER_ID,
                        ADMIN_USER.email(),
                        "Queue Action Admin",
                        // Unique user_handle — 0x7e is taken by DeadLetterRequeueServiceTest's
                        // admin in the shared container (ux_admin_users_user_handle).
                        new byte[] {0x7d},
                        AdminStatus.ACTIVE));
    }

    @Test
    void force_retry_resets_failed_job_to_pending_and_audits() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processing_job(id, job_type, status, attempts, gmail_connection_id,"
                        + " last_failure_reason, last_failed_at)"
                        + " VALUES (?, 'UNSUBSCRIBE_CAMPAIGN', 'FAILED', 3,"
                        + " '00000000-0000-4000-8000-0000000000c1', 'UNKNOWN',"
                        + " NOW() - INTERVAL '5 minutes')",
                jobId);

        int updatedRows =
                runAsAdmin(
                        () ->
                                queueJobActionService.forceRetry(
                                        jobId,
                                        "manual force retry",
                                        "127.0.0.1",
                                        UUID.randomUUID()));
        assertThat(updatedRows).isEqualTo(1);

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT status, attempts, admin_requeue_count, last_failed_at"
                                + " FROM processing_job WHERE id = ?",
                        jobId);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(0);
        assertThat(((Number) row.get("admin_requeue_count")).intValue()).isEqualTo(1);
        assertThat(row.get("last_failed_at")).isNull();

        assertThat(auditCount("JOB_RETRY_FORCED", jobId)).isEqualTo(1L);
        assertThat(beforeStateJson(jobId)).doesNotContainIgnoringCase("payload");
    }

    @Test
    void force_retry_is_idempotent_when_job_not_failed() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processing_job(id, job_type, status, attempts, gmail_connection_id)"
                        + " VALUES (?, 'UNSUBSCRIBE_CAMPAIGN', 'COMPLETED', 1,"
                        + " '00000000-0000-4000-8000-0000000000c1')",
                jobId);

        int updatedRows =
                runAsAdmin(
                        () ->
                                queueJobActionService.forceRetry(
                                        jobId, "noop", "127.0.0.1", UUID.randomUUID()));

        assertThat(updatedRows).isEqualTo(0);
        assertThat(auditCount("JOB_RETRY_FORCED", jobId)).isEqualTo(0L);
    }

    @Test
    void cancel_transitions_pending_job_to_cancelled_and_audits() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processing_job(id, job_type, status, attempts, gmail_connection_id)"
                        + " VALUES (?, 'UNSUBSCRIBE_CAMPAIGN', 'PENDING', 0,"
                        + " '00000000-0000-4000-8000-0000000000c1')",
                jobId);

        int updatedRows =
                runAsAdmin(
                        () ->
                                queueJobActionService.cancel(
                                        jobId, "operator cancel", "127.0.0.1", UUID.randomUUID()));
        assertThat(updatedRows).isEqualTo(1);

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM processing_job WHERE id = ?",
                                String.class,
                                jobId))
                .isEqualTo("CANCELLED");
        assertThat(auditCount("JOB_CANCELLED", jobId)).isEqualTo(1L);
    }

    @Test
    void cancel_refuses_active_processing_job_with_fresh_heartbeat() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processing_job(id, job_type, status, attempts, gmail_connection_id,"
                        + " heartbeat_at)"
                        + " VALUES (?, 'UNSUBSCRIBE_CAMPAIGN', 'PROCESSING', 1,"
                        + " '00000000-0000-4000-8000-0000000000c1', NOW())",
                jobId);

        int updatedRows =
                runAsAdmin(
                        () ->
                                queueJobActionService.cancel(
                                        jobId,
                                        "should be refused",
                                        "127.0.0.1",
                                        UUID.randomUUID()));

        assertThat(updatedRows).isEqualTo(0);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM processing_job WHERE id = ?",
                                String.class,
                                jobId))
                .isEqualTo("PROCESSING");
        assertThat(auditCount("JOB_CANCELLED", jobId)).isEqualTo(0L);
    }

    @Test
    void cancel_allows_stuck_processing_job_with_stale_heartbeat() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processing_job(id, job_type, status, attempts, gmail_connection_id,"
                        + " heartbeat_at)"
                        + " VALUES (?, 'UNSUBSCRIBE_CAMPAIGN', 'PROCESSING', 2,"
                        + " '00000000-0000-4000-8000-0000000000c1', NOW() - INTERVAL '15 minutes')",
                jobId);

        int updatedRows =
                runAsAdmin(
                        () ->
                                queueJobActionService.cancel(
                                        jobId, "stuck worker", "127.0.0.1", UUID.randomUUID()));

        assertThat(updatedRows).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM processing_job WHERE id = ?",
                                String.class,
                                jobId))
                .isEqualTo("CANCELLED");
    }

    private int runAsAdmin(java.util.function.Supplier<Integer> action) {
        return transactionTemplate.execute(_ -> AdminContext.run(ADMIN_USER, action::get));
    }

    private Long auditCount(String action, UUID jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_audit_event WHERE action = ? AND target_id = ?",
                Long.class,
                action,
                jobId);
    }

    private String beforeStateJson(UUID jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT before_state_json::text FROM admin_audit_event WHERE target_id = ?",
                String.class,
                jobId);
    }
}
