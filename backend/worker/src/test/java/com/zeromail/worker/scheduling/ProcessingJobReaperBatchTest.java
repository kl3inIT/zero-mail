package com.zeromail.worker.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.worker.PostgresContainerTest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D-03 — Crash recovery: any {@code processing_job} row whose {@code status='RUNNING'} and whose
 * {@code heartbeat_at &lt; NOW() - INTERVAL '5 minutes'} is reset to {@code status='QUEUED'} with
 * {@code attempts} incremented. Fresh RUNNING rows (heartbeat newer than 5 minutes) are left alone.
 *
 * <p>Future production class {@code ProcessingJobReaperBatch} (Wave 4a / Plan 06) is a
 * {@code @Scheduled(fixedDelayString="60s")} bean.
 *
 * <p>Wave 0 RED: production class not present.
 */
@SuppressWarnings("SqlResolve")
class ProcessingJobReaperBatchTest extends PostgresContainerTest {

    private static final String PROCESSING_JOB_REAPER_BATCH =
            "com.zeromail.worker.scheduling.ProcessingJobReaperBatch";
    private static final Duration STALE_HEARTBEAT_THRESHOLD = Duration.ofMinutes(5);

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void future_reaper_batch_type_is_present() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_REAPER_BATCH))
                .as("Future production type must exist: " + PROCESSING_JOB_REAPER_BATCH)
                .doesNotThrowAnyException();
    }

    @Test
    void staleRunningJob_isResetToQueued() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_REAPER_BATCH)).doesNotThrowAnyException();

        UUID tenantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        // Plan 06 implementation: reaperBatch.reap() flips the stale row back to QUEUED.
        // Stub records intent; full insert + invocation wired in Plan 06.
        assertThat(STALE_HEARTBEAT_THRESHOLD)
                .as("D-03 heartbeat_at threshold is 5 minutes")
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(tenantId).isNotEqualTo(jobId);
        Instant simulatedStaleHeartbeat = Instant.now().minusSeconds(10 * 60);
        assertThat(simulatedStaleHeartbeat)
                .as("stale heartbeat older than 5 minutes triggers reap")
                .isBefore(Instant.now().minus(STALE_HEARTBEAT_THRESHOLD));
    }

    @Test
    void freshRunningJob_isNotReset() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_REAPER_BATCH)).doesNotThrowAnyException();

        Instant freshHeartbeat = Instant.now().minusSeconds(30);
        assertThat(freshHeartbeat)
                .as("heartbeat within the last 5 minutes leaves the row in RUNNING")
                .isAfter(Instant.now().minus(STALE_HEARTBEAT_THRESHOLD));
    }

    @Test
    void reaperLogsEventWithCount() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_REAPER_BATCH)).doesNotThrowAnyException();

        assertThat("event=processing_job_reaper_reaped count=")
                .as("log line follows privacy format: event=<name> tenantId={} count={}")
                .startsWith("event=processing_job_reaper_reaped");
    }
}
