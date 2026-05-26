package com.zeromail.worker.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.worker.PostgresContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * UNS-04a — End-to-end worker pickup with per-sender atomicity.
 *
 * <p>Plan 06 ships the production classes {@code ProcessingJobWorker} + {@code
 * UnsubscribeCampaignHandler} on the worker side, and Plan 07 ships {@code CampaignExecuteService}
 * + {@code UnsubscribeHttpClient} on the core side. Once those exist this test will:
 *
 * <ul>
 *   <li>insert 3 candidate senders into {@code mail_message_observed} via {@link
 *       org.springframework.jdbc.core.JdbcTemplate}
 *   <li>call {@code CampaignExecuteService.execute(...)} → captures {@code jobId}
 *   <li>mock {@code UnsubscribeHttpClient.postOneClick} so senders 1+2 return {@code
 *       UnsubscribeResult.ok(200)} and sender 3 returns {@code
 *       UnsubscribeResult.failed("NETWORK_ERROR")}
 *   <li>mock {@code TriageGmailWriter.applyLabel} / {@code archiveSkipInbox} to track invocations
 *   <li>poll with {@code Awaitility.await().atMost(30, SECONDS)} until {@code processing_job}
 *       reaches a terminal status
 *   <li>assert campaign status = COMPLETED; sender 1+2 attempt state = OK + archive_count &gt; 0;
 *       sender 3 attempt state = FAILED + archive_count = 0 (per-sender atomic)
 * </ul>
 *
 * <p>Wave 0 RED: production classes not present.
 */
@SuppressWarnings("SqlResolve")
class UnsubscribeCampaignE2ETest extends PostgresContainerTest {

    private static final String CAMPAIGN_EXECUTE_SERVICE =
            "com.zeromail.core.cleanup.usecases.CampaignExecuteService";
    private static final String UNSUBSCRIBE_CAMPAIGN_HANDLER =
            "com.zeromail.worker.cleanup.UnsubscribeCampaignHandler";
    private static final String PROCESSING_JOB_WORKER =
            "com.zeromail.worker.cleanup.ProcessingJobWorker";

    @Test
    void future_e2e_types_are_present() {
        assertFutureTypePresent(CAMPAIGN_EXECUTE_SERVICE);
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_HANDLER);
        assertFutureTypePresent(PROCESSING_JOB_WORKER);
    }

    @Test
    @Disabled(
            "Stub assertion (UUID non-null) gives false-positive coverage. Disabled until the"
                    + " real worker-pickup-per-sender-atomic flow is wired against a Postgres test"
                    + " container (Plan 08-01 Task 2.3). Re-enable when the assertion exercises"
                    + " CampaignExecuteService + UnsubscribeCampaignHandler end-to-end.")
    void executeCampaign_workerPicksJob_perSenderAtomic() {
        assertFutureTypePresent(CAMPAIGN_EXECUTE_SERVICE);
        assertFutureTypePresent(UNSUBSCRIBE_CAMPAIGN_HANDLER);

        UUID tenantId = UUID.randomUUID();
        assertThat(tenantId).as("placeholder for fixture tenant").isNotNull();
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 8 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
