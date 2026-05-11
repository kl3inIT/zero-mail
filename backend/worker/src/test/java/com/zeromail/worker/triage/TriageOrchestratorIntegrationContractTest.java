package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageOrchestratorIntegrationContractTest {

    private static final String PLAN_04_WORKER_ORCHESTRATOR_MESSAGE =
            "Wave 0 contract - enabled by 04-01/04-04 when worker triage orchestration lands";
    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.application.TriageOrchestratorService";
    private static final String MAIL_MESSAGE_OBSERVED =
            "com.zeromail.core.gmail.event.MailMessageObserved";
    private static final String TRIAGE_EVENT_RETRY_JOB =
            "com.zeromail.worker.triage.TriageEventRetryJob";
    private static final String TRIAGE_EVENT_CLEANUP_JOB =
            "com.zeromail.worker.triage.TriageEventCleanupJob";
    private static final String TRIAGE_PENDING_REAPER_JOB =
            "com.zeromail.worker.triage.TriagePendingReaperJob";

    @Test
    void future_worker_orchestrator_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(MAIL_MESSAGE_OBSERVED);
        assertFutureTypePresent(TRIAGE_EVENT_RETRY_JOB);
        assertFutureTypePresent(TRIAGE_EVENT_CLEANUP_JOB);
        assertFutureTypePresent(TRIAGE_PENDING_REAPER_JOB);
    }

    @Test
    @Disabled(PLAN_04_WORKER_ORCHESTRATOR_MESSAGE)
    void modulith_event_wiring_processes_two_rule_control_run_once_per_applied_action()
            throws Exception {
        Object orchestratorService = Class.forName(TRIAGE_ORCHESTRATOR_SERVICE)
                .getConstructor()
                .newInstance();
        Method processObservedEventMethod = orchestratorService.getClass().getMethod(
                "processObservedEvent",
                Class.forName(MAIL_MESSAGE_OBSERVED));

        Object result = processObservedEventMethod.invoke(orchestratorService, eventFixture());
        assertThat(result).isEqualTo(Map.of("appliedActions", 2, "auditRows", 2));
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static Object eventFixture() {
        return Map.of(
                "tenantId", "00000000-0000-0000-0000-000000000041",
                "gmailMessageId", "gmail-message-1",
                "gmailThreadId", "thread-1",
                "observedAt", "2026-05-11T00:00:00Z");
    }
}
