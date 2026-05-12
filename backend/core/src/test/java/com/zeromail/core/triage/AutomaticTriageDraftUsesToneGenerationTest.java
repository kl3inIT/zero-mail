package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AutomaticTriageDraftUsesToneGenerationTest {

    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.usecases.TriageOrchestratorService";
    private static final String DRAFT_BODY_GENERATOR =
            "com.zeromail.core.draft.usecases.DraftBodyGenerator";

    @Test
    void automatic_save_draft_uses_tone_matched_generation_not_raw_rule_instruction()
            throws Exception {
        Class<?> orchestratorType = futureType(TRIAGE_ORCHESTRATOR_SERVICE);
        futureType(DRAFT_BODY_GENERATOR);

        assertThat(orchestratorSource())
                .contains("DraftBodyGenerator")
                .contains("draftBodyGenerator.generate(")
                .contains("generateDraftBody")
                .doesNotContain("chatForDraft(");
        assertThat(orchestratorType.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .contains(DRAFT_BODY_GENERATOR);
    }

    private static Class<?> futureType(String futureTypeName) {
        try {
            return Class.forName(futureTypeName);
        } catch (ClassNotFoundException classNotFoundException) {
            fail("not implemented: " + futureTypeName + " missing", classNotFoundException);
            throw new AssertionError("unreachable");
        }
    }

    private static String orchestratorSource() throws Exception {
        return sourceFile(
                "backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java");
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
