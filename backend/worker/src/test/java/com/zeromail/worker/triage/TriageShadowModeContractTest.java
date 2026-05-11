package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TriageShadowModeContractTest {

    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.usecases.TriageOrchestratorService";
    private static final String TRIAGE_DECISION = "com.zeromail.core.triage.domain.TriageDecision";
    private static final String TRIAGE_GMAIL_WRITER =
            "com.zeromail.core.triage.usecases.TriageGmailWriter";

    @Test
    void future_shadow_mode_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(TRIAGE_DECISION);
        assertFutureTypePresent(TRIAGE_GMAIL_WRITER);
    }

    @Test
    void shadow_mode_logs_decision_without_invoking_gmail_writes() throws Exception {
        assertThat(Class.forName(TRIAGE_DECISION).getEnumConstants())
                .extracting(Object::toString)
                .contains("SHADOW_LOGGED");
        assertThat(orchestratorSource())
                .contains("isTriageShadowMode")
                .contains("TriageDecision.SHADOW_LOGGED")
                .contains("recordTerminal(command, TriageDecision.SHADOW_LOGGED)")
                .contains("continue;");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
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
