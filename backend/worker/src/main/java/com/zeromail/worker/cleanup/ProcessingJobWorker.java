package com.zeromail.worker.cleanup;

import com.zeromail.core.cleanup.exception.ThrottleDeferredException;
import com.zeromail.core.queue.domain.JobFailureReason;
import com.zeromail.core.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-02 — Virtual-thread continuous-poll worker over the generic {@code processing_job} outbox.
 *
 * <p>One virtual-thread loop per worker JVM. Each iteration:
 *
 * <ol>
 *   <li>opens a short transaction, executes {@code SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1}
 *       against {@code processing_job} and flips the chosen row to {@code status='PROCESSING'} +
 *       sets {@code heartbeat_at=NOW()}. SKIP LOCKED makes the pickup safe across multiple workers
 *       without an external coordinator.
 *   <li>binds {@code TenantContext.TENANT} and dispatches to the {@code job_type}-specific handler
 *       outside the pickup transaction so the handler can manage its own DB writes (per-attempt
 *       updates, heartbeat refresh, throttle reschedule, etc.).
 *   <li>on success, marks the row {@code COMPLETED}; on {@link ThrottleDeferredException}, leaves
 *       the row {@code PENDING} (the handler has already rescheduled it via {@code next_run_at +
 *       60s}); on any other {@link RuntimeException}, marks the row {@code FAILED} with a {@link
 *       JobFailureReason} enum id (never free-form exception text — see {@code
 *       WorkerFailureReasonEnumOnlyTest}).
 * </ol>
 *
 * <p><b>Status vocabulary</b> matches main's existing CHECK constraint: {@code PENDING / PROCESSING
 * / COMPLETED / FAILED / DEAD_LETTER}. Phase 8 never writes {@code QUEUED} or {@code RUNNING} —
 * those map to {@code PENDING} and {@code PROCESSING} respectively.
 *
 * <p><b>Catch ordering (M-2):</b> {@code ThrottleDeferredException} catch <i>must</i> appear before
 * the generic {@code RuntimeException} catch — see {@code ProcessingJobWorkerThrottleDeferralTest}.
 * Otherwise the row would be overwritten with {@code FAILED} and the deferral semantics would be
 * lost.
 *
 * <p>Privacy invariant: log lines record only {@code tenantId} + {@code jobId} + {@code jobType} —
 * never the payload contents.
 */
@Component
@SuppressWarnings("SqlResolve")
public class ProcessingJobWorker {

    private static final Logger log = LoggerFactory.getLogger(ProcessingJobWorker.class);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(5);

    private static final String CLAIM_SELECT_SQL =
            """
            SELECT id, tenant_id, job_type, payload_json::text AS payload
              FROM processing_job
             WHERE status = 'PENDING'
               AND next_run_at <= NOW()
             ORDER BY created_at
             LIMIT 1
               FOR UPDATE SKIP LOCKED
            """;
    private static final String CLAIM_UPDATE_SQL =
            """
            UPDATE processing_job
               SET status = 'PROCESSING',
                   started_at = NOW(),
                   heartbeat_at = NOW(),
                   updated_at = NOW(),
                   attempts = attempts + 1
             WHERE id = ?
            """;
    private static final String MARK_COMPLETED_SQL =
            """
            UPDATE processing_job
               SET status = 'COMPLETED',
                   completed_at = NOW(),
                   heartbeat_at = NULL,
                   updated_at = NOW()
             WHERE id = ?
            """;
    private static final String MARK_FAILED_SQL =
            """
            UPDATE processing_job
               SET status = 'FAILED',
                   completed_at = NOW(),
                   heartbeat_at = NULL,
                   last_failure_reason = ?,
                   last_failed_at = NOW(),
                   updated_at = NOW()
             WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final UnsubscribeCampaignHandler unsubscribeCampaignHandler;
    private final com.zeromail.worker.inbox.InboxBackfillJobHandler inboxBackfillJobHandler;

    private volatile boolean shouldRun = true;
    private Thread pollLoopThread;

    public ProcessingJobWorker(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            UnsubscribeCampaignHandler unsubscribeCampaignHandler,
            com.zeromail.worker.inbox.InboxBackfillJobHandler inboxBackfillJobHandler) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.unsubscribeCampaignHandler =
                Objects.requireNonNull(
                        unsubscribeCampaignHandler, "unsubscribeCampaignHandler must not be null");
        this.inboxBackfillJobHandler =
                Objects.requireNonNull(
                        inboxBackfillJobHandler, "inboxBackfillJobHandler must not be null");
    }

    @PostConstruct
    void startPolling() {
        this.pollLoopThread =
                Thread.ofVirtual().name("processing-job-worker").start(this::pollLoop);
    }

    @PreDestroy
    void stopPolling() throws InterruptedException {
        shouldRun = false;
        if (pollLoopThread != null) {
            pollLoopThread.join(SHUTDOWN_GRACE.toMillis());
        }
    }

    private void pollLoop() {
        while (shouldRun) {
            try {
                Optional<ClaimedJob> claimedJob =
                        transactionTemplate.execute(transactionStatus -> claimNextJob());
                if (claimedJob == null || claimedJob.isEmpty()) {
                    Thread.sleep(POLL_INTERVAL);
                    continue;
                }
                dispatchJob(claimedJob.get());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException unexpectedFailure) {
                log.error(
                        "event=processing_job_worker_error failureType={}",
                        unexpectedFailure.getClass().getSimpleName(),
                        unexpectedFailure);
                try {
                    Thread.sleep(POLL_INTERVAL);
                } catch (InterruptedException sleepInterrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private Optional<ClaimedJob> claimNextJob() {
        return jdbcTemplate.query(
                CLAIM_SELECT_SQL,
                resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.<ClaimedJob>empty();
                    }
                    UUID jobId = resultSet.getObject("id", UUID.class);
                    UUID tenantId = resultSet.getObject("tenant_id", UUID.class);
                    String jobType = resultSet.getString("job_type");
                    String payload = resultSet.getString("payload");
                    jdbcTemplate.update(CLAIM_UPDATE_SQL, jobId);
                    log.info(
                            "event=processing_job_picked tenantId={} jobId={} jobType={}",
                            tenantId,
                            jobId,
                            jobType);
                    return Optional.of(new ClaimedJob(jobId, tenantId, jobType, payload));
                });
    }

    private void dispatchJob(ClaimedJob claimedJob) {
        try {
            ScopedValue.where(TenantContext.TENANT, claimedJob.tenantId().toString())
                    .run(() -> invokeHandler(claimedJob));
            markCompleted(claimedJob.jobId());
        } catch (ThrottleDeferredException throttleDeferred) {
            // M-2: handler has already re-queued the row (status='PENDING', next_run_at=NOW()+60s).
            // DO NOT mark FAILED — that would overwrite the PENDING reschedule. Just log + exit.
            log.info(
                    "event=processing_job_throttle_deferred tenantId={} jobId={} senderDomain={} reason={}",
                    throttleDeferred.tenantId(),
                    claimedJob.jobId(),
                    throttleDeferred.senderDomain(),
                    throttleDeferred.reason());
        } catch (RuntimeException handlerFailure) {
            JobFailureReason mappedFailureReason = mapToFailureReason(handlerFailure);
            if (isTransientConcurrencyConflict(handlerFailure)) {
                // Two workers raced the same aggregate row (JPA optimistic lock). This is a benign,
                // expected-degraded outcome under concurrent polling — not an actionable server
                // fault — so it is logged at WARN without a stack trace instead of drowning the
                // ERROR stream. The row is still failed-and-recorded below for operator visibility.
                log.warn(
                        "event=processing_job_handler_conflict tenantId={} jobId={} failureType={}",
                        claimedJob.tenantId(),
                        claimedJob.jobId(),
                        handlerFailure.getClass().getSimpleName());
            } else {
                log.error(
                        "event=processing_job_handler_failed tenantId={} jobId={} failureReason={}"
                                + " failureType={}",
                        claimedJob.tenantId(),
                        claimedJob.jobId(),
                        mappedFailureReason.id(),
                        handlerFailure.getClass().getSimpleName(),
                        handlerFailure);
            }
            markFailed(claimedJob.jobId(), mappedFailureReason);
        }
    }

    private void invokeHandler(ClaimedJob claimedJob) {
        switch (claimedJob.jobType()) {
            case "UNSUBSCRIBE_CAMPAIGN" ->
                    unsubscribeCampaignHandler.handle(
                            claimedJob.jobId(), claimedJob.tenantId(), claimedJob.payload());
            case "INBOX_PROJECTION_BACKFILL" ->
                    inboxBackfillJobHandler.handle(
                            claimedJob.jobId(), claimedJob.tenantId(), claimedJob.payload());
            default ->
                    throw new IllegalStateException(
                            "Unknown processing_job.job_type: " + claimedJob.jobType());
        }
    }

    /**
     * Map a runtime exception to a {@link JobFailureReason} enum id. The mapping is intentionally
     * conservative — anything we cannot classify lands in {@link JobFailureReason#UNKNOWN} so the
     * DB CHECK constraint never rejects a worker write. Cleanup-specific exception classes are
     * mapped to their dedicated codes; everything else falls through to {@code UNKNOWN}.
     */
    private static JobFailureReason mapToFailureReason(RuntimeException handlerFailure) {
        String simpleName = handlerFailure.getClass().getSimpleName();
        return switch (simpleName) {
            case "SuppressedSenderException" -> JobFailureReason.VALIDATION_FAILED;
            case "CampaignCapExceededException" -> JobFailureReason.VALIDATION_FAILED;
            default -> JobFailureReason.UNKNOWN;
        };
    }

    /**
     * Detects a transient JPA optimistic-lock / concurrency conflict anywhere in the cause chain.
     * Matched by simple class name so the worker module does not need a compile dependency on
     * spring-orm's exception types (they are raised by core's JPA handler). Such conflicts mean two
     * workers picked up work touching the same aggregate; they are benign and self-correct on the
     * next poll, so they are logged at WARN rather than ERROR.
     */
    private static boolean isTransientConcurrencyConflict(RuntimeException handlerFailure) {
        Throwable current = handlerFailure;
        int depth = 0;
        while (current != null && depth < 5) {
            String simpleName = current.getClass().getSimpleName();
            if ("ObjectOptimisticLockingFailureException".equals(simpleName)
                    || "OptimisticLockingFailureException".equals(simpleName)
                    || "StaleObjectStateException".equals(simpleName)
                    || "StaleStateException".equals(simpleName)
                    || "CannotAcquireLockException".equals(simpleName)
                    || "ConcurrencyFailureException".equals(simpleName)) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private void markCompleted(UUID jobId) {
        jdbcTemplate.update(MARK_COMPLETED_SQL, jobId);
    }

    private void markFailed(UUID jobId, JobFailureReason failureReason) {
        jdbcTemplate.update(MARK_FAILED_SQL, failureReason.id(), jobId);
    }

    /** Pickup-loop projection of a single processing_job row claimed via SKIP LOCKED. */
    private record ClaimedJob(UUID jobId, UUID tenantId, String jobType, String payload) {}
}
