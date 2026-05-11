package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageCreditAccountingContractTest {

    private static final String PLAN_04_CREDIT_ACCOUNTING_MESSAGE =
            "Wave 0 contract - enabled by 04-04 when triage credit accounting lands";
    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.application.TriageOrchestratorService";
    private static final String CREDIT_LEDGER =
            "com.zeromail.core.billing.service.CreditLedger";
    private static final String TRIAGE_PLATFORM_LLM = "TRIAGE_PLATFORM_LLM";
    private static final String TRIAGE_DETERMINISTIC = "TRIAGE_DETERMINISTIC";

    @Test
    void future_credit_accounting_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(CREDIT_LEDGER);
    }

    @Test
    @Disabled(PLAN_04_CREDIT_ACCOUNTING_MESSAGE)
    void llm_messages_reserve_once_per_llm_call_and_deterministic_messages_reserve_once()
            throws Exception {
        Object orchestratorService = Class.forName(TRIAGE_ORCHESTRATOR_SERVICE)
                .getConstructor()
                .newInstance();
        Method creditProbeMethod = orchestratorService.getClass().getMethod("creditReservationsForTest");

        Object creditProbe = creditProbeMethod.invoke(orchestratorService);

        assertThat(metric(creditProbe, TRIAGE_PLATFORM_LLM)).isEqualTo(1);
        assertThat(metric(creditProbe, TRIAGE_DETERMINISTIC)).isEqualTo(1);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static int metric(Object creditProbe, String callSiteName) throws Exception {
        Method countMethod = creditProbe.getClass().getMethod("countFor", String.class);
        return (Integer) countMethod.invoke(creditProbe, callSiteName);
    }
}
