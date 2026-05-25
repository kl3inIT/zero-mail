package com.zeromail.worker.scheduling;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-25 daily retention purge for the {@code processing_job} outbox. Terminal rows ({@code status IN
 * ('COMPLETED','FAILED','DEAD_LETTER')}) older than 90 days are deleted in batches of at most 1000
 * per invocation; non-terminal rows (PENDING, PROCESSING) are always retained. Status vocabulary
 * matches main's ck_processing_job_status CHECK.
 *
 * <p><b>D-25 invariant:</b> the linked audit tables {@code unsubscribe_campaign} and {@code
 * unsubscribe_attempt} are <i>never</i> touched by this batch — audit trail unsubscribe_campaign +
 * unsubscribe_attempt KEEP FOREVER for support + analytics retro. Only the worker-lifecycle row in
 * {@code processing_job} is bounded.
 *
 * <p>Runs daily at 03:00 UTC via {@code @Scheduled(cron)}; ShedLock prevents multi-instance
 * double-fire.
 */
@Component
@SuppressWarnings("SqlResolve")
public class ProcessingJobPurgeBatch {

    static final String LOCK_NAME = "processingJobPurge";

    private static final Logger log = LoggerFactory.getLogger(ProcessingJobPurgeBatch.class);
    private static final Duration PROCESSING_JOB_RETENTION = Duration.ofDays(90);
    private static final int BATCH_LIMIT = 1_000;

    /**
     * Safety cap on the outer loop. With a 1000-row batch we can absorb up to one million expired
     * rows per nightly run before declaring the worker behind on cleanup; if we ever hit that,
     * operations should investigate why retention drained so far.
     */
    private static final int MAX_BATCHES_PER_INVOCATION = 1_000;

    private static final String PURGE_SQL =
            """
            WITH expired_jobs AS (
              SELECT id
                FROM processing_job
               WHERE completed_at < ?
                 AND status IN ('COMPLETED', 'FAILED', 'DEAD_LETTER')
               ORDER BY completed_at ASC, id ASC
               LIMIT ?
                 FOR UPDATE SKIP LOCKED
            ),
            deleted_jobs AS (
              DELETE FROM processing_job
               WHERE id IN (SELECT id FROM expired_jobs)
               RETURNING id
            )
            SELECT
              (SELECT COUNT(*) FROM expired_jobs) AS selected_count,
              (SELECT COUNT(*) FROM deleted_jobs) AS deleted_count
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ProcessingJobPurgeBatch(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void scheduledPurge() {
        LockAssert.assertLocked();
        purge();
    }

    /**
     * Purge expired terminal {@code processing_job} rows in batches of {@value BATCH_LIMIT}. Loops
     * until the batch undershoots its limit or the safety cap fires. Returns the total deleted row
     * count.
     */
    public int purge() {
        Instant retentionCutoff = clock.instant().minus(PROCESSING_JOB_RETENTION);
        int totalDeleted = 0;
        int batchIteration = 0;
        int selectedCount;
        do {
            PurgeResult result = purgeExpiredOnce(retentionCutoff);
            selectedCount = result.selectedCount();
            totalDeleted += result.deletedCount();
            batchIteration++;
        } while (selectedCount == BATCH_LIMIT && batchIteration < MAX_BATCHES_PER_INVOCATION);
        log.info("event=processing_job_purged tenantId=system totalDeleted={}", totalDeleted);
        return totalDeleted;
    }

    /**
     * Delete one batch of at most {@value BATCH_LIMIT} expired terminal {@code processing_job}
     * rows. Returns the rows-selected vs rows-deleted breakdown so the caller can detect that the
     * batch hit its cap and another iteration is warranted.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PurgeResult purgeExpiredOnce(Instant retentionCutoff) {
        return jdbcTemplate.queryForObject(
                PURGE_SQL,
                (resultSet, rowNumber) ->
                        new PurgeResult(
                                resultSet.getInt("selected_count"),
                                resultSet.getInt("deleted_count")),
                Timestamp.from(retentionCutoff),
                BATCH_LIMIT);
    }

    public record PurgeResult(int selectedCount, int deletedCount) {}
}
