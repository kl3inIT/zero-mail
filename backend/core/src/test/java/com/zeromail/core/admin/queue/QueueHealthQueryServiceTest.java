package com.zeromail.core.admin.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.zeromail.core.admin.queue.projection.QueueDepthByType;
import com.zeromail.core.admin.queue.projection.QueueHealthSnapshot;
import com.zeromail.core.admin.queue.usecases.QueueHealthQueryService;
import com.zeromail.core.support.PostgresContainerTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class QueueHealthQueryServiceTest extends PostgresContainerTest {

    @Autowired private QueueHealthQueryService queueHealthQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void truncateProcessingJob() {
        jdbcTemplate.execute("TRUNCATE TABLE processing_job CASCADE");
    }

    @Test
    void snapshot_aggregates_depth_dead_letters_and_oldest_pending_age() {
        // Fixture: 10 PENDING + 5 PROCESSING + 3 DEAD_LETTER rows over two job types.
        seedJob("TRIAGE_INCOMING", "PENDING", 0, 8);
        seedJob("DRAFT_REPLY", "PENDING", 0, 2);
        seedJob("TRIAGE_INCOMING", "PROCESSING", 1, 4);
        seedJob("DRAFT_REPLY", "PROCESSING", 1, 1);
        seedJob("TRIAGE_INCOMING", "DEAD_LETTER", 5, 3);
        // Bump one PENDING row's created_at back ~5 minutes to test oldest age.
        jdbcTemplate.update(
                "UPDATE processing_job SET created_at = NOW() - INTERVAL '5 minutes', "
                        + " locked_until = NULL"
                        + " WHERE job_type = 'TRIAGE_INCOMING' AND status = 'PENDING'"
                        + " AND id IN (SELECT id FROM processing_job"
                        + "            WHERE job_type='TRIAGE_INCOMING' AND status='PENDING'"
                        + "            ORDER BY created_at ASC LIMIT 1)");

        QueueHealthSnapshot snapshot = queueHealthQueryService.snapshot();

        assertThat(snapshot.depthByType())
                .extracting(QueueDepthByType::jobType)
                .containsExactlyInAnyOrder("DRAFT_REPLY", "TRIAGE_INCOMING");
        QueueDepthByType triage =
                snapshot.depthByType().stream()
                        .filter(row -> row.jobType().equals("TRIAGE_INCOMING"))
                        .findFirst()
                        .orElseThrow();
        assertThat(triage.pendingCount()).isEqualTo(8);
        assertThat(triage.processingCount()).isEqualTo(4);

        assertThat(snapshot.deadLetterCount()).isEqualTo(3);
        assertThat(snapshot.oldestUnleasedJobAge())
                .as("oldest unleased PENDING job is about 5 minutes old")
                .isBetween(Duration.ofMinutes(4), Duration.ofMinutes(6));
        // Histogram is dense 0..4 — sum of row counts equals total non-deleted rows we seeded.
        int histogramTotal =
                snapshot.retryHistogram().stream().mapToInt(bucket -> bucket.rowCount()).sum();
        assertThat(histogramTotal).isEqualTo(8 + 2 + 4 + 1 + 3);
    }

    @Test
    void failure_rate_uses_24h_bounded_denominator() {
        // R-8E-H2 acceptance: 100 PENDING from 30 days ago + 5 FAILED in last 24h + 95 SUCCEEDED
        // in last 24h should yield 5/100 = 0.05 (NOT 5/200 = 0.025 lifetime).
        // We approximate "SUCCEEDED" as COMPLETED, which is the CHECK-allowed terminal.
        for (int rowIndex = 0; rowIndex < 100; rowIndex++) {
            jdbcTemplate.update(
                    "INSERT INTO processing_job(job_type, status, attempts, gmail_connection_id,"
                            + " created_at)"
                            + " VALUES (?, 'PENDING', 0, '00000000-0000-4000-8000-0000000000c1',"
                            + " NOW() - INTERVAL '30 days')",
                    "OLD_PENDING_JOB");
        }
        for (int rowIndex = 0; rowIndex < 5; rowIndex++) {
            jdbcTemplate.update(
                    "INSERT INTO processing_job(job_type, status, attempts, gmail_connection_id,"
                            + " created_at, last_failed_at)"
                            + " VALUES (?, 'FAILED', 5, '00000000-0000-4000-8000-0000000000c1',"
                            + " NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour')",
                    "RECENT_FAILED_JOB");
        }
        for (int rowIndex = 0; rowIndex < 95; rowIndex++) {
            jdbcTemplate.update(
                    "INSERT INTO processing_job(job_type, status, attempts, gmail_connection_id,"
                            + " created_at, completed_at)"
                            + " VALUES (?, 'COMPLETED', 1, '00000000-0000-4000-8000-0000000000c1',"
                            + " NOW() - INTERVAL '3 hours', NOW())",
                    "RECENT_OK_JOB");
        }

        QueueHealthSnapshot snapshot = queueHealthQueryService.snapshot();

        // Denominator is 100 rows created in the last 24h (5 FAILED + 95 COMPLETED), numerator is
        // the 5 FAILED rows; the 100 old PENDING rows are correctly excluded from both sides.
        assertThat(snapshot.failureRateLast24h()).isCloseTo(0.05, within(0.001));
        // Small-sample guard inputs: the raw counts must be exposed so the UI can render "5/100"
        // (or suppress the percentage entirely when the sample is tiny) instead of a bare ratio.
        assertThat(snapshot.failedCountLast24h()).isEqualTo(5);
        assertThat(snapshot.sampleSizeLast24h()).isEqualTo(100);
    }

    private java.util.List<UUID> seedJob(String jobType, String status, int attempts, int count) {
        java.util.List<UUID> ids = new java.util.ArrayList<>(count);
        for (int rowIndex = 0; rowIndex < count; rowIndex++) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO processing_job(id, job_type, status, attempts, gmail_connection_id)"
                            + " VALUES (?, ?, ?, ?, '00000000-0000-4000-8000-0000000000c1')",
                    id,
                    jobType,
                    status,
                    attempts);
            ids.add(id);
        }
        return ids;
    }
}
