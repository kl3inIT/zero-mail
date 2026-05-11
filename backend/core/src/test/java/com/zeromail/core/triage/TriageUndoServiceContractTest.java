package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageUndoServiceContractTest {

    private static final String PLAN_04_UNDO_SERVICE_MESSAGE =
            "Wave 0 contract - enabled by 04-06 when triage undo lands";
    private static final String TRIAGE_UNDO_SERVICE =
            "com.zeromail.core.triage.application.TriageUndoService";
    private static final String TRIAGE_GMAIL_WRITER =
            "com.zeromail.core.triage.service.TriageGmailWriter";
    private static final String TRIAGE_UNDO_EXPIRED_EXCEPTION =
            "com.zeromail.core.triage.exception.TriageUndoExpiredException";
    private static final String TRIAGE_UNDO_ALREADY_DONE_EXCEPTION =
            "com.zeromail.core.triage.exception.TriageUndoAlreadyDoneException";
    private static final String TRIAGE_UNDO_UNSUPPORTED_ACTION_EXCEPTION =
            "com.zeromail.core.triage.exception.TriageUndoUnsupportedActionException";
    private static final String TRIAGE_AUDIT_EXCEPTION =
            "com.zeromail.core.triage.exception.TriageAuditException";

    @Test
    void future_undo_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_UNDO_SERVICE);
        assertFutureTypePresent(TRIAGE_GMAIL_WRITER);
        assertFutureTypePresent(TRIAGE_UNDO_EXPIRED_EXCEPTION);
        assertFutureTypePresent(TRIAGE_UNDO_ALREADY_DONE_EXCEPTION);
        assertFutureTypePresent(TRIAGE_UNDO_UNSUPPORTED_ACTION_EXCEPTION);
        assertFutureTypePresent(TRIAGE_AUDIT_EXCEPTION);
    }

    @Test
    @Disabled(PLAN_04_UNDO_SERVICE_MESSAGE)
    void undo_computes_inverse_for_each_supported_action_result() throws Exception {
        Object undoService = Class.forName(TRIAGE_UNDO_SERVICE).getConstructor().newInstance();
        Method inverseMethod = undoService.getClass().getMethod(
                "computeInverse",
                Class.forName("com.zeromail.core.triage.domain.TriageActionResult"));

        assertThat(inverseMethod).isNotNull();
    }

    @Test
    @Disabled(PLAN_04_UNDO_SERVICE_MESSAGE)
    void expired_already_done_and_unsupported_actions_fail_with_reason_specific_exceptions()
            throws Exception {
        Object undoService = Class.forName(TRIAGE_UNDO_SERVICE).getConstructor().newInstance();
        Method undoWithinWindowMethod = undoService.getClass().getMethod("undoWithinWindow", Object.class, Duration.class);

        assertThatThrownBy(() -> undoWithinWindowMethod.invoke(undoService, "audit-1", Duration.ofDays(30).plusSeconds(1)))
                .hasRootCauseInstanceOf(throwableType(TRIAGE_UNDO_EXPIRED_EXCEPTION));
        assertThatThrownBy(() -> undoWithinWindowMethod.invoke(undoService, "already-reverted", Duration.ZERO))
                .hasRootCauseInstanceOf(throwableType(TRIAGE_UNDO_ALREADY_DONE_EXCEPTION));
        assertThatThrownBy(() -> undoWithinWindowMethod.invoke(undoService, "unknown-action", Duration.ZERO))
                .hasRootCauseInstanceOf(throwableType(TRIAGE_UNDO_UNSUPPORTED_ACTION_EXCEPTION));
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static Class<? extends Throwable> throwableType(String futureTypeName) throws ClassNotFoundException {
        return Class.forName(futureTypeName).asSubclass(Throwable.class);
    }
}
