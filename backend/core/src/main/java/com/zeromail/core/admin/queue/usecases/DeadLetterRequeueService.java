package com.zeromail.core.admin.queue.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dead-letter requeue use case. Resets {@code attempts=0} so the worker gets a fresh retry budget,
 * and increments {@code admin_requeue_count} (R-8E-H1) so repeat-offender jobs surface in the
 * queue-health KPIs.
 *
 * <p><b>Privacy invariant (T-08-47 + OPS-QUEUE-02):</b> this service never reads the stored job
 * body column; the {@code before_state_json} written to the audit row contains only the job id, job
 * type, attempts-before-requeue, last_failure_reason, and admin_requeue_count-before. The audit
 * chain is HMAC-hashed by {@link AdminAuditWriter#append}.
 *
 * <p>Operation is idempotent: a second call against the same job (no longer in DEAD_LETTER status)
 * returns 0 without writing an audit row.
 */
@Service
public class DeadLetterRequeueService {

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditWriter adminAuditWriter;

    public DeadLetterRequeueService(JdbcTemplate jdbcTemplate, AdminAuditWriter adminAuditWriter) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
    }

    /**
     * Returns the number of rows transitioned from DEAD_LETTER to PENDING; 0 means the job was not
     * in DEAD_LETTER at call time (idempotent path).
     */
    @Transactional
    public int requeue(UUID jobId, String reason, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        UUID targetJobId = Objects.requireNonNull(jobId, "jobId must not be null");

        Map<String, Object> beforeStateMap = readJobState(targetJobId);
        if (beforeStateMap == null || !"DEAD_LETTER".equals(beforeStateMap.get("status"))) {
            return 0;
        }

        // Reset attempts to 0 (fresh retry budget), increment admin_requeue_count (R-8E-H1),
        // clear last_failed_at + locked_until, and stamp last_requeued_at = NOW().
        // The UPDATE intentionally never touches the stored job body column.
        int updatedRows =
                jdbcTemplate.update(
                        """
                        UPDATE processing_job
                        SET status = 'PENDING',
                            attempts = 0,
                            locked_until = NULL,
                            locked_at = NULL,
                            last_failed_at = NULL,
                            admin_requeue_count = admin_requeue_count + 1,
                            last_requeued_at = NOW(),
                            updated_at = NOW()
                        WHERE id = ? AND status = 'DEAD_LETTER'
                        """,
                        targetJobId);

        if (updatedRows == 0) {
            // Lost the race against another admin / janitor; treat as idempotent no-op.
            return 0;
        }

        String beforeStateJson = formatBeforeState(beforeStateMap);
        String afterStateJson = formatAfterState(beforeStateMap);

        adminAuditWriter.append(
                AdminAuditAction.DEAD_LETTER_REQUEUED,
                "PROCESSING_JOB",
                targetJobId,
                beforeStateJson,
                afterStateJson,
                reason,
                requestIp,
                requestId);

        return updatedRows;
    }

    /** Reads only metadata fields — never the stored job body column. */
    private Map<String, Object> readJobState(UUID jobId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT id::text AS id,
                           job_type AS "jobType",
                           status,
                           attempts,
                           admin_requeue_count AS "adminRequeueCount",
                           last_failure_reason AS "lastFailureReason"
                    FROM processing_job
                    WHERE id = ?
                    """,
                    jobId);
        } catch (org.springframework.dao.EmptyResultDataAccessException emptyResult) {
            return null;
        }
    }

    private static String formatBeforeState(Map<String, Object> beforeStateMap) {
        String jobId = String.valueOf(beforeStateMap.get("id"));
        String jobType = jsonQuoted(String.valueOf(beforeStateMap.get("jobType")));
        int attempts = ((Number) beforeStateMap.get("attempts")).intValue();
        int adminRequeueCount = ((Number) beforeStateMap.get("adminRequeueCount")).intValue();
        Object lastFailureReason = beforeStateMap.get("lastFailureReason");
        String lastFailureReasonJson =
                lastFailureReason == null ? "null" : jsonQuoted(String.valueOf(lastFailureReason));
        return "{\"jobId\":\""
                + jobId
                + "\",\"jobType\":"
                + jobType
                + ",\"attemptsBeforeRequeue\":"
                + attempts
                + ",\"adminRequeueCountBefore\":"
                + adminRequeueCount
                + ",\"lastFailureReason\":"
                + lastFailureReasonJson
                + "}";
    }

    private static String formatAfterState(Map<String, Object> beforeStateMap) {
        int adminRequeueCount = ((Number) beforeStateMap.get("adminRequeueCount")).intValue();
        return "{\"status\":\"PENDING\",\"attempts\":0,\"adminRequeueCountAfter\":"
                + (adminRequeueCount + 1)
                + "}";
    }

    private static String jsonQuoted(String value) {
        // Minimal JSON string escaper — values are short enum-like job_type names and the
        // JobFailureReason enum, all ASCII without quote/control characters in production. The
        // AdminAuditWriter canonicalises via Postgres jsonb cast so any malformed string will fail
        // at the DB boundary, surfacing the bug rather than silently corrupting the chain.
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
