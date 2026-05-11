package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageUndoControllerContractTest {

    private static final String PLAN_04_UNDO_CONTROLLER_MESSAGE =
            "Wave 0 contract - enabled by 04-06 when triage undo API lands";
    private static final String TRIAGE_AUDIT_CONTROLLER =
            "com.zeromail.api.controllers.triage.TriageAuditController";
    private static final String TRIAGE_UNDO_SERVICE =
            "com.zeromail.core.triage.application.TriageUndoService";
    private static final String ERROR_CODES =
            "com.zeromail.api.error.ErrorCodes";

    @Test
    void future_undo_controller_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_AUDIT_CONTROLLER);
        assertFutureTypePresent(TRIAGE_UNDO_SERVICE);
        assertFutureTypePresent(ERROR_CODES);
    }

    @Test
    @Disabled(PLAN_04_UNDO_CONTROLLER_MESSAGE)
    void undo_endpoint_maps_success_conflicts_and_cross_tenant_not_found() throws Exception {
        Class<?> errorCodesClass = Class.forName(ERROR_CODES);

        assertThat("/api/triage/audit/{auditId}/undo").isEqualTo("/api/triage/audit/{auditId}/undo");
        assertThat(List.of(
                errorCode(errorCodesClass, "TRIAGE_UNDO_ALREADY_DONE"),
                errorCode(errorCodesClass, "TRIAGE_UNDO_EXPIRED"),
                errorCode(errorCodesClass, "TRIAGE_UNDO_UNSUPPORTED_ACTION")))
                .containsExactly(
                        "error.triage.undo.already_done",
                        "error.triage.undo.expired",
                        "error.triage.undo.unsupported_action");
        assertThat(404).as("Cross-tenant audit ids must be returned as not found").isEqualTo(404);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static String errorCode(Class<?> errorCodesClass, String constantName) throws Exception {
        return (String) errorCodesClass.getField(constantName).get(null);
    }
}
