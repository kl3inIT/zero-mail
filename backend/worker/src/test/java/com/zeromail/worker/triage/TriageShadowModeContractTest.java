package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageShadowModeContractTest {

    private static final String PLAN_04_SHADOW_MODE_MESSAGE =
            "Wave 0 contract - enabled by 04-05 when triage shadow mode lands";
    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.application.TriageOrchestratorService";
    private static final String TRIAGE_DECISION =
            "com.zeromail.core.triage.domain.TriageDecision";
    private static final String TRIAGE_GMAIL_WRITER =
            "com.zeromail.core.triage.service.TriageGmailWriter";

    @Test
    void future_shadow_mode_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(TRIAGE_DECISION);
        assertFutureTypePresent(TRIAGE_GMAIL_WRITER);
    }

    @Test
    @Disabled(PLAN_04_SHADOW_MODE_MESSAGE)
    void shadow_mode_logs_decision_without_invoking_gmail_writes() throws Exception {
        Object orchestratorService = Class.forName(TRIAGE_ORCHESTRATOR_SERVICE)
                .getConstructor()
                .newInstance();
        Method runInShadowModeMethod = orchestratorService.getClass().getMethod("runInShadowModeForTest");

        Object result = runInShadowModeMethod.invoke(orchestratorService);

        assertThat(metric(result, "decision")).isEqualTo("SHADOW_LOGGED");
        assertThat(metric(result, "gmailWriteCount")).isEqualTo(0);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static Object metric(Object result, String metricName) throws Exception {
        Method metricMethod = result.getClass().getMethod(metricName);
        return metricMethod.invoke(result);
    }
}
