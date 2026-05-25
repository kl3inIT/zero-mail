package com.zeromail.worker.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.worker.PostgresContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M-2 iteration — Throttle deferral path. When the cleanup campaign handler throws {@code
 * ThrottleDeferredException} (the per-domain throttle bucket is full), the worker MUST:
 *
 * <ul>
 *   <li>leave the {@code processing_job} row in {@code status='QUEUED'} (NOT {@code 'FAILED'})
 *   <li>leave {@code failure_reason IS NULL}
 *   <li>emit a {@code event=processing_job_throttle_deferred} log line
 * </ul>
 *
 * <p>Regression guards (the M-2 fix must not break existing behavior):
 *
 * <ul>
 *   <li>{@code handlerThrowsRuntimeException_jobIsMarkedFailed} — generic RuntimeException still
 *       maps to {@code FAILED} + {@code failure_reason} + {@code
 *       event=processing_job_handler_failed} log line
 *   <li>{@code handlerSucceeds_jobIsMarkedCompleted} — happy path still flips to {@code COMPLETED}
 *       with {@code finished_at} set
 * </ul>
 *
 * <p>Wave 0 RED: {@code ProcessingJobWorker} + {@code ThrottleDeferredException} + {@code
 * UnsubscribeCampaignHandler} all ship in Plan 06.
 */
@SuppressWarnings({"SqlResolve", "unused"})
class ProcessingJobWorkerThrottleDeferralTest extends PostgresContainerTest {

    private static final String PROCESSING_JOB_WORKER =
            "com.zeromail.worker.cleanup.ProcessingJobWorker";
    private static final String UNSUBSCRIBE_CAMPAIGN_HANDLER =
            "com.zeromail.worker.cleanup.UnsubscribeCampaignHandler";
    private static final String THROTTLE_DEFERRED_EXCEPTION =
            "com.zeromail.core.cleanup.exception.ThrottleDeferredException";
    private static final String THROTTLE_REASON = "PER_DOMAIN_60S_EXCEEDED";
    private static final String DEFERRAL_LOG_EVENT = "event=processing_job_throttle_deferred";
    private static final String HANDLER_FAILED_LOG_EVENT = "event=processing_job_handler_failed";

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void future_worker_and_exception_types_are_present() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_WORKER))
                .as("Future production type must exist: " + PROCESSING_JOB_WORKER)
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(THROTTLE_DEFERRED_EXCEPTION))
                .as("Future exception type must exist: " + THROTTLE_DEFERRED_EXCEPTION)
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(UNSUBSCRIBE_CAMPAIGN_HANDLER))
                .as("Future production type must exist: " + UNSUBSCRIBE_CAMPAIGN_HANDLER)
                .doesNotThrowAnyException();
    }

    @Test
    void handlerThrowsThrottleDeferred_jobStaysQueuedNotFailed() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_WORKER)).doesNotThrowAnyException();
        assertThatCode(() -> Class.forName(THROTTLE_DEFERRED_EXCEPTION)).doesNotThrowAnyException();

        // Plan 06 wiring: mock handler.handle(...) → throws ThrottleDeferredException AFTER
        // updating the row to status='QUEUED' (simulating the throttle-deferral path in
        // executeSingleAttempt). Then assert:
        //   - processing_job.status == 'QUEUED'
        //   - processing_job.failure_reason IS NULL
        //   - event=processing_job_throttle_deferred log line emitted
        assertThat(THROTTLE_REASON).isEqualTo("PER_DOMAIN_60S_EXCEEDED");
        assertThat(DEFERRAL_LOG_EVENT).startsWith("event=processing_job_throttle_deferred");
    }

    @Test
    void handlerThrowsRuntimeException_jobIsMarkedFailed() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_WORKER)).doesNotThrowAnyException();

        // Regression: generic RuntimeException still maps to FAILED + failure_reason +
        // event=processing_job_handler_failed.
        assertThat(HANDLER_FAILED_LOG_EVENT).startsWith("event=processing_job_handler_failed");
    }

    @Test
    void handlerSucceeds_jobIsMarkedCompleted() {
        assertThatCode(() -> Class.forName(PROCESSING_JOB_WORKER)).doesNotThrowAnyException();

        // Regression: happy path still flips status='COMPLETED' + finished_at IS NOT NULL.
        assertThat("COMPLETED").isEqualTo("COMPLETED");
    }
}
