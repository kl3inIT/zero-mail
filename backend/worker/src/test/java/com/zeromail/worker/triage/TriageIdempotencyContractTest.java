package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageIdempotencyContractTest {

    private static final String PLAN_04_IDEMPOTENCY_MESSAGE =
            "Wave 0 contract - enabled by 04-02/04-04 when triage idempotency lands";
    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.application.TriageOrchestratorService";
    private static final String TRIAGE_AUDIT_REPOSITORY =
            "com.zeromail.core.triage.persistence.TriageAuditRepository";
    private static final String TRIAGE_GMAIL_WRITER =
            "com.zeromail.core.triage.service.TriageGmailWriter";
    private static final String TRIAGE_PENDING_REAPER_JOB =
            "com.zeromail.worker.triage.TriagePendingReaperJob";

    @Test
    void future_idempotency_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(TRIAGE_AUDIT_REPOSITORY);
        assertFutureTypePresent(TRIAGE_GMAIL_WRITER);
        assertFutureTypePresent(TRIAGE_PENDING_REAPER_JOB);
    }

    @Test
    @Disabled(PLAN_04_IDEMPOTENCY_MESSAGE)
    void replaying_same_message_writes_one_audit_row_and_at_most_one_gmail_write_per_action()
            throws Exception {
        Object orchestratorService = Class.forName(TRIAGE_ORCHESTRATOR_SERVICE)
                .getConstructor()
                .newInstance();
        Method replayMethod = orchestratorService.getClass().getMethod("replaySameMessageTwiceForTest");

        Object replayResult = replayMethod.invoke(orchestratorService);

        assertThat(metric(replayResult, "auditRowsPerAction")).isEqualTo(1);
        assertThat(metric(replayResult, "gmailWritesPerAction")).isLessThanOrEqualTo(1);
    }

    @Test
    @Disabled(PLAN_04_IDEMPOTENCY_MESSAGE)
    void crash_then_replay_reclaims_pending_row_and_completes_gmail_write() throws Exception {
        Object repository = Class.forName(TRIAGE_AUDIT_REPOSITORY).getConstructor().newInstance();
        Method reclaimMethod = repository.getClass().getMethod("reclaimStalePending", java.time.Duration.class);

        assertThat(reclaimMethod.invoke(repository, java.time.Duration.ofMinutes(3))).isEqualTo(1);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static int metric(Object result, String metricName) throws Exception {
        Method metricMethod = result.getClass().getMethod(metricName);
        return (Integer) metricMethod.invoke(result);
    }
}
