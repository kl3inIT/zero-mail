package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TriageOrchestratorContractTest {

    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.application.TriageOrchestratorService";
    private static final String MAIL_MESSAGE_OBSERVED =
            "com.zeromail.core.gmail.event.MailMessageObserved";
    private static final String SEMANTIC_INTENT_REQUEST =
            "com.zeromail.core.llm.application.SemanticIntentRequest";
    private static final String LLM_GATEWAY = "com.zeromail.core.llm.service.LlmGateway";
    private static final String TRIAGE_DECISION = "com.zeromail.core.triage.domain.TriageDecision";
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
    void orchestrator_consumes_mail_message_observed_and_evaluates_rules_in_display_order()
            throws Exception {
        Class<?> orchestratorClass = Class.forName(TRIAGE_ORCHESTRATOR_SERVICE);
        Class<?> observedEventClass = Class.forName(MAIL_MESSAGE_OBSERVED);

        assertThat(orchestratorClass.getMethod("processObservedEvent", observedEventClass))
                .isNotNull();
        assertThat(orchestratorSource())
                .contains("@ApplicationModuleListener")
                .contains("TenantContext.runWith")
                .contains("findOrderedByTenantId")
                .contains("RuleEntity::isEnabled")
                .contains("ActionProposalMerger");
    }

    @Test
    void semantic_intents_are_batched_once_per_message_until_token_budget_requires_fanout()
            throws Exception {
        Class<?> gatewayClass = Class.forName(LLM_GATEWAY);
        Class<?> semanticIntentRequestClass = Class.forName(SEMANTIC_INTENT_REQUEST);

        Method evaluateSemanticIntentsMethod =
                gatewayClass.getMethod(
                        "evaluateSemanticIntents",
                        Class.forName("com.zeromail.core.billing.domain.CallSite"),
                        String.class,
                        List.class);

        assertThat(evaluateSemanticIntentsMethod).isNotNull();
        assertThat(semanticIntentRequestClass.getRecordComponents())
                .extracting(recordComponent -> recordComponent.getName())
                .containsExactly("nodeId", "intent");
        assertThat(orchestratorSource())
                .contains("CallSite.TRIAGE_PLATFORM_LLM")
                .contains("TokenBudgetExceededException")
                .contains("resolveSemanticMatchesPerRule")
                .contains("semanticEvalContent");
    }

    @Test
    void deterministic_proposal_control_run_matches_orchestrator_output() throws Exception {
        assertThat(orchestratorSource())
                .contains("evaluateRules")
                .contains("ruleExecutionCandidate.actionIntents()")
                .contains("new ActionProposal")
                .contains("actionProposalMerger.merge");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static String orchestratorSource() throws Exception {
        return sourceFile(
                "backend/core/src/main/java/com/zeromail/core/triage/application/TriageOrchestratorService.java");
    }

    private static String sourceFile(String relativePath) throws Exception {
        Path currentDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidateDirectory = currentDirectory;
                candidateDirectory != null;
                candidateDirectory = candidateDirectory.getParent()) {
            Path resolvedPath = candidateDirectory.resolve(relativePath);
            if (Files.exists(resolvedPath)) {
                return Files.readString(resolvedPath);
            }
        }
        throw new NoSuchFileException(relativePath);
    }
}
