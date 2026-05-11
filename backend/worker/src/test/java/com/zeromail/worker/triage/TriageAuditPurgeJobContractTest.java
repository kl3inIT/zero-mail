package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageAuditPurgeJobContractTest {

    private static final String PLAN_04_PURGE_MESSAGE =
            "Wave 0 contract - enabled by 04-07 when triage audit purge lands";
    private static final String TRIAGE_AUDIT_PURGE_JOB =
            "com.zeromail.worker.triage.TriageAuditPurgeJob";
    private static final String TRIAGE_AUDIT_REPOSITORY =
            "com.zeromail.core.triage.persistence.TriageAuditRepository";
    private static final String TRIAGE_DECISION =
            "com.zeromail.core.triage.domain.TriageDecision";

    @Test
    void future_purge_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_AUDIT_PURGE_JOB);
        assertFutureTypePresent(TRIAGE_AUDIT_REPOSITORY);
        assertFutureTypePresent(TRIAGE_DECISION);
    }

    @Test
    @Disabled(PLAN_04_PURGE_MESSAGE)
    void purge_deletes_aged_terminal_rows_by_decided_at_including_shadow_logged() throws Exception {
        Object purgeJob = Class.forName(TRIAGE_AUDIT_PURGE_JOB).getConstructor().newInstance();
        Method purgeMethod = purgeJob.getClass().getMethod("purgeOlderThan", Duration.class);

        Object purgeResult = purgeMethod.invoke(purgeJob, Duration.ofDays(30));

        assertThat(metric(purgeResult, "deletedAppliedRows")).isGreaterThan(0);
        assertThat(metric(purgeResult, "deletedShadowLoggedRows")).isGreaterThan(0);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static int metric(Object purgeResult, String metricName) throws Exception {
        Method metricMethod = purgeResult.getClass().getMethod(metricName);
        return (Integer) metricMethod.invoke(purgeResult);
    }
}
