package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.rules.domain.ActionIntent;
import com.zeromail.core.rules.domain.ActionProposal;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.exception.TriageSafetyViolationException;
import com.zeromail.core.triage.service.TriageSafetyPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TriageSafetyPolicyContractTest {

    private static final String TRIAGE_SAFETY_POLICY =
            "com.zeromail.core.triage.service.TriageSafetyPolicy";
    private static final String TRIAGE_SAFETY_VIOLATION_EXCEPTION =
            "com.zeromail.core.triage.exception.TriageSafetyViolationException";
    private static final String TRIAGE_AUDIT_WRITER =
            "com.zeromail.core.triage.persistence.TriageAuditWriter";

    @Test
    void future_safety_policy_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_SAFETY_POLICY);
        assertFutureTypePresent(TRIAGE_SAFETY_VIOLATION_EXCEPTION);
        assertFutureTypePresent(TRIAGE_AUDIT_WRITER);
    }

    @Test
    void policy_rejects_null_action_before_any_gmail_call() {
        TriageSafetyPolicy safetyPolicy = new TriageSafetyPolicy();

        assertThatThrownBy(() -> safetyPolicy.gate(null))
                .isInstanceOf(TriageSafetyViolationException.class);
        assertThat(TriageSafetyPolicy.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName().contains("Gmail"));
    }

    @Test
    void policy_accepts_all_v1_allow_listed_actions() {
        TriageSafetyPolicy safetyPolicy = new TriageSafetyPolicy();

        assertThat(safetyPolicy.gate(proposal(new ActionIntent.Label("Finance"))))
                .isEqualTo(RuleActionType.LABEL);
        assertThat(safetyPolicy.gate(proposal(new ActionIntent.Archive())))
                .isEqualTo(RuleActionType.ARCHIVE);
        assertThat(
                        safetyPolicy.gate(
                                proposal(new ActionIntent.SaveDraft("Draft a reply for review"))))
                .isEqualTo(RuleActionType.SAVE_DRAFT);
    }

    @Test
    void rejected_actions_are_recorded_as_safety_policy_audit_decisions() throws Exception {
        Class<?> triageDecisionClass =
                Class.forName("com.zeromail.core.triage.domain.TriageDecision");
        Object rejectedBySafetyPolicy =
                Enum.valueOf(
                        triageDecisionClass.asSubclass(Enum.class), "REJECTED_BY_SAFETY_POLICY");

        assertThat(rejectedBySafetyPolicy.toString()).isEqualTo("REJECTED_BY_SAFETY_POLICY");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static ActionProposal proposal(ActionIntent actionIntent) {
        return new ActionProposal(
                actionIntent,
                List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                List.of("rule-name"),
                List.of("evidence-id"));
    }

    @Test
    void rule_action_type_send_must_not_exist() {
        assertThatThrownBy(() -> RuleActionType.valueOf("SEND"))
                .as("RuleActionType.SEND must not exist in v1")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
