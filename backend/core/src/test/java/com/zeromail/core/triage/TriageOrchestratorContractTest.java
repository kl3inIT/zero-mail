package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageOrchestratorContractTest {

    private static final String PLAN_04_ORCHESTRATOR_MESSAGE =
            "Wave 0 contract - enabled by 04-01/04-04 when the triage orchestrator lands";
    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.application.TriageOrchestratorService";
    private static final String MAIL_MESSAGE_OBSERVED =
            "com.zeromail.core.gmail.event.MailMessageObserved";
    private static final String SEMANTIC_INTENT_REQUEST =
            "com.zeromail.core.llm.application.SemanticIntentRequest";
    private static final String LLM_GATEWAY =
            "com.zeromail.core.llm.service.LlmGateway";
    private static final String TRIAGE_DECISION =
            "com.zeromail.core.triage.domain.TriageDecision";
    private static final String TRIAGE_AUDIT_WRITER =
            "com.zeromail.core.triage.persistence.TriageAuditWriter";

    @Test
    void future_orchestrator_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(MAIL_MESSAGE_OBSERVED);
        assertFutureTypePresent(SEMANTIC_INTENT_REQUEST);
        assertFutureTypePresent(TRIAGE_DECISION);
        assertFutureTypePresent(TRIAGE_AUDIT_WRITER);
    }

    @Test
    @Disabled(PLAN_04_ORCHESTRATOR_MESSAGE)
    void orchestrator_consumes_mail_message_observed_and_evaluates_rules_in_display_order()
            throws Exception {
        Object orchestratorService = Class.forName(TRIAGE_ORCHESTRATOR_SERVICE)
                .getConstructor()
                .newInstance();
        Class<?> observedEventClass = Class.forName(MAIL_MESSAGE_OBSERVED);
        Method orchestrateMethod = orchestratorService.getClass().getMethod("orchestrate", observedEventClass);

        Object orchestrationResult = orchestrateMethod.invoke(orchestratorService, observedEventFixture());

        Method evaluatedRuleIdsMethod = orchestrationResult.getClass().getMethod("evaluatedRuleIds");
        assertThat(evaluatedRuleIdsMethod.invoke(orchestrationResult))
                .as("Rules must be evaluated in display_order, not database iteration order")
                .isEqualTo(List.of("rule-a", "rule-b"));
    }

    @Test
    @Disabled(PLAN_04_ORCHESTRATOR_MESSAGE)
    void semantic_intents_are_batched_once_per_message_until_token_budget_requires_fanout()
            throws Exception {
        Class<?> gatewayClass = Class.forName(LLM_GATEWAY);
        Class<?> semanticIntentRequestClass = Class.forName(SEMANTIC_INTENT_REQUEST);

        Method evaluateSemanticIntentsMethod =
                gatewayClass.getMethod("evaluateSemanticIntents", Class.forName(
                                "com.zeromail.core.billing.domain.CallSite"),
                        String.class, List.class);

        assertThat(evaluateSemanticIntentsMethod).isNotNull();
        assertThat(semanticIntentRequestClass.getRecordComponents())
                .extracting(recordComponent -> recordComponent.getName())
                .containsExactly("nodeId", "intent");
    }

    @Test
    @Disabled(PLAN_04_ORCHESTRATOR_MESSAGE)
    void deterministic_proposal_control_run_matches_orchestrator_output() throws Exception {
        Object orchestratorService = Class.forName(TRIAGE_ORCHESTRATOR_SERVICE)
                .getConstructor()
                .newInstance();
        Method deterministicControlRunMethod =
                orchestratorService.getClass().getMethod("deterministicControlRun", Map.class);
        Method orchestrateForFixtureMethod =
                orchestratorService.getClass().getMethod("orchestrateForFixture", Map.class);

        Object controlRun = deterministicControlRunMethod.invoke(orchestratorService, twoRuleFixture());
        Object actualRun = orchestrateForFixtureMethod.invoke(orchestratorService, twoRuleFixture());

        assertThat(actualRun).isEqualTo(controlRun);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static Object observedEventFixture() {
        return Map.of(
                "tenantId", "00000000-0000-0000-0000-000000000041",
                "gmailMessageId", "gmail-message-1",
                "gmailThreadId", "thread-1",
                "observedAt", "2026-05-11T00:00:00Z");
    }

    private static Map<String, Object> twoRuleFixture() {
        return Map.of(
                "rules", List.of("rule-a", "rule-b"),
                "gmailMessageId", "gmail-message-1",
                "sanitizedMessageContent", "invoice from vendor");
    }
}
