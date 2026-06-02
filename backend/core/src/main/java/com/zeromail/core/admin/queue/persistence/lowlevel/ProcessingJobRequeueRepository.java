package com.zeromail.core.admin.queue.persistence.lowlevel;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read + UPDATE access to {@code processing_job} for the dead-letter requeue use case.
 *
 * <p>Privacy invariant (T-08-47 + OPS-QUEUE-02): every SELECT list omits the stored job body
 * column; the requeue UPDATE never touches it either.
 */
@Repository
public class ProcessingJobRequeueRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessingJobRequeueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /** Reads only metadata fields — never the stored job body column. Returns null on miss. */
    public Map<String, Object> findMetadata(UUID jobId) {
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
        } catch (EmptyResultDataAccessException emptyResult) {
            return null;
        }
    }

    /**
     * Resets attempts to 0 (fresh retry budget), increments {@code admin_requeue_count} (R-8E-H1),
     * clears the lease/failure timestamps, and stamps {@code last_requeued_at = NOW()}.
     *
     * @return number of rows transitioned from DEAD_LETTER to PENDING (0 on idempotent re-call).
     */
    public int requeueFromDeadLetter(UUID jobId) {
        return jdbcTemplate.update(
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
                jobId);
    }

    /**
     * Force-retries a FAILED job: resets {@code attempts=0}, clears the lease/failure timestamps,
     * re-arms {@code next_run_at=NOW()}, and counts the intervention as an admin requeue (R-8E-H1).
     * For a CATALOG_SYNC job the consumer only claims rows whose payload step is {@code FETCH}, so
     * the step is reset too — that field is operator job state, never email content.
     *
     * @return number of rows transitioned from FAILED to PENDING (0 if not FAILED at call time).
     */
    public int forceRetryFromFailed(UUID jobId) {
        return jdbcTemplate.update(
                """
                UPDATE processing_job
                SET status = 'PENDING',
                    attempts = 0,
                    locked_until = NULL,
                    locked_at = NULL,
                    last_failed_at = NULL,
                    next_run_at = NOW(),
                    admin_requeue_count = admin_requeue_count + 1,
                    last_requeued_at = NOW(),
                    updated_at = NOW(),
                    payload_json = CASE WHEN job_type = 'CATALOG_SYNC'
                                        THEN jsonb_set(payload_json, '{step}', '"FETCH"', true)
                                        ELSE payload_json END
                WHERE id = ? AND status = 'FAILED'
                """,
                jobId);
    }

    /**
     * Cancels a job. A PENDING job is always cancellable; a PROCESSING job only when its heartbeat
     * is stale (worker presumed dead) so we never mark a row CANCELLED out from under a live worker
     * that would then write COMPLETED/FAILED over it. CANCELLED is terminal — the pickup query
     * never selects it.
     *
     * @return number of rows transitioned to CANCELLED (0 if not in a cancellable state).
     */
    public int cancel(UUID jobId, Duration stuckGrace) {
        return jdbcTemplate.update(
                """
                UPDATE processing_job
                SET status = 'CANCELLED',
                    locked_until = NULL,
                    locked_at = NULL,
                    heartbeat_at = NULL,
                    completed_at = NOW(),
                    updated_at = NOW()
                WHERE id = ?
                  AND (status = 'PENDING'
                       OR (status = 'PROCESSING'
                           AND (heartbeat_at IS NULL
                                OR heartbeat_at < NOW() - (? * INTERVAL '1 second'))))
                """,
                jobId,
                stuckGrace.toSeconds());
    }
}
