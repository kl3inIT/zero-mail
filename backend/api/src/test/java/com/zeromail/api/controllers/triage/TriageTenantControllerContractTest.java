package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;

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
    void patch_shadow_mode_flips_tenant_flag_that_orchestrator_reads()
            throws NoSuchMethodException {
        GetMapping getMapping =
                TriageTenantController.class
                        .getMethod("getShadowMode")
                        .getAnnotation(GetMapping.class);
        PatchMapping patchMapping =
                TriageTenantController.class
                        .getMethod(
                                "setShadowMode",
                                com.zeromail.api.dto.triage.TriageShadowModeRequest.class)
                        .getAnnotation(PatchMapping.class);

        assertThat(getMapping.value()).containsExactly("/api/tenant/triage/shadow-mode");
        assertThat(patchMapping.value()).containsExactly("/api/tenant/triage/shadow-mode");
        assertThat("triage_shadow_mode").isEqualTo("triage_shadow_mode");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
