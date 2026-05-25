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

/**
 * D-03 crash recovery for the {@code processing_job} outbox. Any row with {@code
 * status='PROCESSING'} and a {@code heartbeat_at} older than 5 minutes is reset back to {@code
 * status='PENDING'} with {@code attempts} incremented, so a fresh worker pickup loop can re-claim
 * it via {@code FOR UPDATE SKIP LOCKED}.
 *
 * <p>Runs every 60 seconds across the worker fleet, guarded by ShedLock so only one node actually
 * reaps per tick. Mirrors {@code DigestPendingReaperJob} in style.
 */
@Component
@SuppressWarnings("SqlResolve")
public class ProcessingJobReaperBatch {

    static final String LOCK_NAME = "processingJobReaper";

    private static final Logger log = LoggerFactory.getLogger(ProcessingJobReaperBatch.class);
    private static final Duration STALE_HEARTBEAT_GRACE = Duration.ofMinutes(5);
    private static final String REAP_SQL =
            """
            UPDATE processing_job
               SET status = 'PENDING',
                   attempts = attempts + 1,
                   heartbeat_at = NULL,
                   started_at = NULL,
                   next_run_at = NOW(),
                   updated_at = NOW()
             WHERE status = 'PROCESSING'
               AND heartbeat_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ProcessingJobReaperBatch(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(fixedDelayString = "PT60S")
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void scheduledReap() {
        LockAssert.assertLocked();
        reap();
    }

    /** Reset all stale PROCESSING rows to PENDING + attempts++. Returns the row-count reaped. */
    public int reap() {
        Instant staleCutoff = clock.instant().minus(STALE_HEARTBEAT_GRACE);
        int reapedCount = jdbcTemplate.update(REAP_SQL, Timestamp.from(staleCutoff));
        log.info("event=processing_job_reaper_reaped tenantId=system count={}", reapedCount);
        return reapedCount;
    }
}
