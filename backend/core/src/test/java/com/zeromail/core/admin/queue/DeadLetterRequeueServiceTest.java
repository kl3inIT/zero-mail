package com.zeromail.core.admin.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.admin.auth.persistence.AdminUserEntity;
import com.zeromail.core.admin.auth.persistence.AdminUserRepository;
import com.zeromail.core.admin.queue.usecases.DeadLetterRequeueService;
import com.zeromail.core.support.PostgresContainerTest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class DeadLetterRequeueServiceTest extends PostgresContainerTest {

    private static final UUID ADMIN_USER_ID =
            UUID.fromString("00000000-0000-4000-8000-0000000008e1");
    private static final AdminUser ADMIN_USER =
            new AdminUser(
                    ADMIN_USER_ID,
                    "queue-admin@example.com",
                    AdminStatus.ACTIVE,
                    Optional.of("Queue Admin"));

    @Autowired private DeadLetterRequeueService deadLetterRequeueService;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM admin_read_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event DISABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM admin_audit_event");
        jdbcTemplate.execute(
                "ALTER TABLE admin_audit_event ENABLE TRIGGER admin_audit_event_append_only");
        jdbcTemplate.execute("DELETE FROM admin_users");
        jdbcTemplate.execute("DELETE FROM processing_job");
        adminUserRepository.save(
                new AdminUserEntity(
                        ADMIN_USER_ID,
                        ADMIN_USER.email(),
                        "Queue Admin",
                        new byte[] {0x7e},
                        AdminStatus.ACTIVE));
    }

    @Test
    void requeue_resets_attempts_increments_admin_count_and_writes_audit_row() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processing_job(id, job_type, status, attempts,"
                        + " last_failure_reason, last_failed_at)"
                        + " VALUES (?, 'TRIAGE_INCOMING', 'DEAD_LETTER', 5, 'DOWNSTREAM_TIMEOUT',"
                        + " NOW() - INTERVAL '10 minutes')",
                jobId);

        int updatedRows =
                transactionTemplate.execute(
                        _ ->
                                AdminContext.run(
                                        ADMIN_USER,
                                        () ->
                                                deadLetterRequeueService.requeue(
                                                        jobId,
                                                        "transient downstream",
                                                        "127.0.0.1",
                                                        UUID.randomUUID())));
        assertThat(updatedRows).isEqualTo(1);

        Map<String, Object> updatedRow =
                jdbcTemplate.queryForMap(
                        "SELECT status, attempts, admin_requeue_count, last_failure_reason,"
                                + " last_failed_at, last_requeued_at"
                                + " FROM processing_job WHERE id = ?",
                        jobId);
        assertThat(updatedRow.get("status")).isEqualTo("PENDING");
        assertThat(((Number) updatedRow.get("attempts")).intValue()).isEqualTo(0);
        assertThat(((Number) updatedRow.get("admin_requeue_count")).intValue()).isEqualTo(1);
        assertThat(updatedRow.get("last_failed_at")).isNull();
        assertThat(updatedRow.get("last_requeued_at")).isNotNull();

        Long auditRowCount =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM admin_audit_event WHERE action = 'DEAD_LETTER_REQUEUED'"
                                + " AND target_id = ?",
                        Long.class,
                        jobId);
        assertThat(auditRowCount).isEqualTo(1L);

        String beforeStateJson =
                jdbcTemplate.queryForObject(
                        "SELECT before_state_json::text FROM admin_audit_event WHERE"
                                + " target_id = ?",
                        String.class,
                        jobId);
        // Privacy invariant: no payload key in before_state_json.
        assertThat(beforeStateJson)
                .doesNotContainIgnoringCase("payload")
                .contains("attemptsBeforeRequeue")
                .contains("DOWNSTREAM_TIMEOUT");
    }

    @Test
    void requeue_is_idempotent_when_job_already_pending() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processing_job(id, job_type, status, attempts)"
                        + " VALUES (?, 'TRIAGE_INCOMING', 'PENDING', 0)",
                jobId);

        int updatedRows =
                transactionTemplate.execute(
                        _ ->
                                AdminContext.run(
                                        ADMIN_USER,
                                        () ->
                                                deadLetterRequeueService.requeue(
                                                        jobId,
                                                        "noop reason",
                                                        "127.0.0.1",
                                                        UUID.randomUUID())));
        assertThat(updatedRows).isEqualTo(0);

        Long auditRowCount =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM admin_audit_event WHERE target_id = ?",
                        Long.class,
                        jobId);
        assertThat(auditRowCount).isEqualTo(0L);
    }

    @Test
    void requeue_requires_admin_context() {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO processing_job(id, job_type, status, attempts)"
                        + " VALUES (?, 'TRIAGE_INCOMING', 'DEAD_LETTER', 5)",
                jobId);

        org.assertj.core.api.Assertions.assertThatIllegalStateException()
                .isThrownBy(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        _ ->
                                                deadLetterRequeueService.requeue(
                                                        jobId,
                                                        "reason without admin context",
                                                        "127.0.0.1",
                                                        UUID.randomUUID())));
    }
}
