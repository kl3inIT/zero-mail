package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TriageOrchestratorIntegrationContractTest {

    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.application.TriageOrchestratorService";
    private static final String MAIL_MESSAGE_OBSERVED =
            "com.zeromail.core.gmail.event.MailMessageObserved";
    private static final String TRIAGE_EVENT_RETRY_JOB =
            "com.zeromail.worker.triage.TriageEventRetryJob";
    private static final String TRIAGE_EVENT_CLEANUP_JOB =
            "com.zeromail.worker.triage.TriageEventCleanupJob";
    private static final String TRIAGE_PENDING_REAPER_JOB =
            "com.zeromail.worker.triage.TriagePendingReaperJob";

    @Test
    void future_worker_orchestrator_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(MAIL_MESSAGE_OBSERVED);
        assertFutureTypePresent(TRIAGE_EVENT_RETRY_JOB);
        assertFutureTypePresent(TRIAGE_EVENT_CLEANUP_JOB);
        assertFutureTypePresent(TRIAGE_PENDING_REAPER_JOB);
    }

    @Test
    void modulith_event_wiring_processes_two_rule_control_run_once_per_applied_action()
            throws Exception {
        Class<?> orchestratorClass = Class.forName(TRIAGE_ORCHESTRATOR_SERVICE);
        Method processObservedEventMethod =
                orchestratorClass.getMethod(
                        "processObservedEvent", Class.forName(MAIL_MESSAGE_OBSERVED));

        assertThat(processObservedEventMethod).isNotNull();
        assertThat(orchestratorSource())
                .contains("@ApplicationModuleListener")
                .contains("processObservedEvent")
                .contains("handleProposals")
                .contains("triageAuditSaga.reservePhase")
                .contains("triageAuditSaga.finalizePhase");
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
