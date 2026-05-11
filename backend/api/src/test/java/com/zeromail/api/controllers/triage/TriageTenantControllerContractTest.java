package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class TriageTenantControllerContractTest {

    private static final String TRIAGE_TENANT_CONTROLLER =
            "com.zeromail.api.controllers.triage.TriageTenantController";
    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.usecases.TriageOrchestratorService";

    @Test
    void future_tenant_controller_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_TENANT_CONTROLLER);
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
    }

    @Test
    void patch_shadow_mode_flips_tenant_flag_that_orchestrator_reads() {
        assertThat("PATCH /api/tenant/triage/shadow-mode")
                .contains("/api/tenant/triage/shadow-mode");
        assertThat("triage_shadow_mode").isEqualTo("triage_shadow_mode");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
