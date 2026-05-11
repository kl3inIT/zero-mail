package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageSafetyPolicyContractTest {

    private static final String PLAN_04_SAFETY_POLICY_MESSAGE =
            "Wave 0 contract - enabled by 04-04 when triage safety policy lands";
    private static final String TRIAGE_SAFETY_POLICY =
            "com.zeromail.core.triage.service.TriageSafetyPolicy";
    private static final String TRIAGE_GMAIL_WRITER =
            "com.zeromail.core.triage.service.TriageGmailWriter";
    private static final String TRIAGE_SAFETY_VIOLATION_EXCEPTION =
            "com.zeromail.core.triage.exception.TriageSafetyViolationException";
    private static final String TRIAGE_AUDIT_WRITER =
            "com.zeromail.core.triage.persistence.TriageAuditWriter";

    @Test
    void future_safety_policy_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_SAFETY_POLICY);
        assertFutureTypePresent(TRIAGE_GMAIL_WRITER);
        assertFutureTypePresent(TRIAGE_SAFETY_VIOLATION_EXCEPTION);
        assertFutureTypePresent(TRIAGE_AUDIT_WRITER);
    }

    @Test
    @Disabled(PLAN_04_SAFETY_POLICY_MESSAGE)
    void policy_rejects_non_allow_listed_action_before_any_gmail_call() throws Exception {
        Object safetyPolicy = Class.forName(TRIAGE_SAFETY_POLICY).getConstructor().newInstance();
        Method gateMethod = safetyPolicy.getClass().getMethod("gate", Class.forName(
                "com.zeromail.core.rules.domain.RuleActionType"));

        assertThatThrownBy(() -> gateMethod.invoke(safetyPolicy, unsupportedActionType()))
                .hasRootCauseInstanceOf(throwableType(TRIAGE_SAFETY_VIOLATION_EXCEPTION));
        assertThat(gmailWriteInvocationCount(safetyPolicy)).isZero();
    }

    @Test
    @Disabled(PLAN_04_SAFETY_POLICY_MESSAGE)
    void rejected_actions_are_recorded_as_safety_policy_audit_decisions() throws Exception {
        Class<?> triageDecisionClass = Class.forName("com.zeromail.core.triage.domain.TriageDecision");
        Object rejectedBySafetyPolicy = Enum.valueOf(
                triageDecisionClass.asSubclass(Enum.class),
                "REJECTED_BY_SAFETY_POLICY");

        assertThat(rejectedBySafetyPolicy.toString()).isEqualTo("REJECTED_BY_SAFETY_POLICY");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static Object unsupportedActionType() throws Exception {
        Class<?> actionTypeClass = Class.forName("com.zeromail.core.rules.domain.RuleActionType");
        assertThatThrownBy(() -> Enum.valueOf(actionTypeClass.asSubclass(Enum.class), "SEND"))
                .as("RuleActionType.SEND must not exist in v1")
                .isInstanceOf(IllegalArgumentException.class);
        return Enum.valueOf(actionTypeClass.asSubclass(Enum.class), "ARCHIVE");
    }

    private static int gmailWriteInvocationCount(Object safetyPolicy) throws Exception {
        Method invocationCountMethod = safetyPolicy.getClass().getMethod("gmailWriteInvocationCountForTest");
        return (Integer) invocationCountMethod.invoke(safetyPolicy);
    }

    private static Class<? extends Throwable> throwableType(String futureTypeName) throws ClassNotFoundException {
        return Class.forName(futureTypeName).asSubclass(Throwable.class);
    }
}
