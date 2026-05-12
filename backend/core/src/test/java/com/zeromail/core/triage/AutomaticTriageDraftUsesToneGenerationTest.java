package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class AutomaticTriageDraftUsesToneGenerationTest {

    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.usecases.TriageOrchestratorService";
    private static final String GENERATE_THREAD_DRAFT_SERVICE =
            "com.zeromail.core.draft.usecases.GenerateThreadDraftService";

    @Test
    void automatic_save_draft_uses_tone_matched_generation_not_raw_rule_instruction() {
        Class<?> orchestratorType = futureType(TRIAGE_ORCHESTRATOR_SERVICE);
        futureType(GENERATE_THREAD_DRAFT_SERVICE);

        fail(
                "not implemented: "
                        + orchestratorType.getName()
                        + " must route automatic save_draft body creation through GenerateThreadDraftService");
    }

    private static Class<?> futureType(String futureTypeName) {
        try {
            return Class.forName(futureTypeName);
        } catch (ClassNotFoundException classNotFoundException) {
            fail("not implemented: " + futureTypeName + " missing", classNotFoundException);
            throw new AssertionError("unreachable");
        }
    }
}
