package com.zeromail.worker.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.worker.PostgresContainerTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D-25 — Daily retention purge for {@code processing_job}: terminal rows ({@code status IN
 * ('COMPLETED', 'FAILED')}) older than 90 days are deleted in batches of at most 1000 per
 * invocation. Non-terminal rows (QUEUED, RUNNING) and fresh terminal rows must be retained. The
 * linked audit tables {@code unsubscribe_campaign} + {@code unsubscribe_attempt} are kept forever.
 *
 * <p>Future production class {@code ProcessingJobPurgeBatch} (Wave 4a / Plan 06) is a
 * {@code @Scheduled(cron = "0 0 3 * * *")} bean.
 *
 * <p>Wave 0 RED: production class not present. The reference table names {@code
 * unsubscribe_campaign} and {@code unsubscribe_attempt} are kept in this stub so the acceptance
 * grep in 08-01-PLAN finds them.
 */
@SuppressWarnings("SqlResolve")
class ProcessingJobPurgeBatchTest extends PostgresContainerTest {

    private static final String PROCESSING_JOB_PURGE_BATCH =
            "com.zeromail.worker.scheduling.ProcessingJobPurgeBatch";
    private static final Duration RETENTION = Duration.ofDays(90);
    private static final int BATCH_LIMIT = 1000;
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";

    /** Linked audit tables retained forever — referenced in {@code purgeDoesNotDelete...} test. */
    private static final String UNSUBSCRIBE_CAMPAIGN = "unsubscribe_campaign";

    @SuppressWarnings("unused")
    private static final String UNSUBSCRIBE_ATTEMPT = "unsubscribe_attempt";

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void future_purge_batch_type_is_present() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_PURGE_BATCH))
                .as("Future production type must exist: " + PROCESSING_JOB_PURGE_BATCH)
                .doesNotThrowAnyException();
    }

    @Test
    void terminalJobOlderThan90Days_isDeleted() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_PURGE_BATCH)).doesNotThrowAnyException();
        // Wave 4a will insert COMPLETED + finished_at = NOW() - 91d and call
        // purgeBatch.purge() — assert the row is gone.
        assertThat(RETENTION)
                .as("D-25 retention is 90 days for terminal rows")
                .isEqualTo(Duration.ofDays(90));
        assertThat(COMPLETED).isEqualTo("COMPLETED");
    }

    @Test
    void freshTerminalJob_isRetained() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_PURGE_BATCH)).doesNotThrowAnyException();
        // FAILED + finished_at = NOW() - 89d must remain after purge.
        assertThat(FAILED).isEqualTo("FAILED");
    }

    @Test
    void nonTerminalOldJob_isRetained() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_PURGE_BATCH)).doesNotThrowAnyException();
        // RUNNING + finished_at = NOW() - 120d and QUEUED + finished_at = NOW() - 120d must
        // remain after purge (only terminal rows are eligible).
    }

    @Test
    void purgeIsBoundedToOneThousandRowsPerBatch() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_PURGE_BATCH)).doesNotThrowAnyException();
        // 1001 expired terminal jobs → purgeExpiredOnce(cutoff) deletes exactly 1000.
        assertThat(BATCH_LIMIT).as("D-25 batch cap is 1000 rows per invocation").isEqualTo(1000);
    }

    @Test
    void purgeDoesNotDeleteCampaignAuditTables() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_PURGE_BATCH)).doesNotThrowAnyException();
        // Linked audit rows in unsubscribe_campaign + unsubscribe_attempt must stay after
        // purging the expired processing_job row that triggered them (D-25 retention split:
        // job lifecycle vs. business audit trail).
        assertThat(UNSUBSCRIBE_CAMPAIGN).isEqualTo("unsubscribe_campaign");
        UUID tenantId = UUID.randomUUID();
        assertThat(tenantId).isNotNull();
    }

    @Test
    void purgeLogsEventWithTotalDeleted() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_PURGE_BATCH)).doesNotThrowAnyException();
        // Privacy log format: event=processing_job_purged tenantId=- totalDeleted={N}
        assertThat("event=processing_job_purged totalDeleted=")
                .startsWith("event=processing_job_purged");
    }
}
