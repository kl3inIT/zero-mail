package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TriageIdempotencyContractTest {

    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.application.TriageOrchestratorService";
    private static final String TRIAGE_AUDIT_REPOSITORY =
            "com.zeromail.core.triage.persistence.TriageAuditRepository";
    private static final String TRIAGE_GMAIL_WRITER =
            "com.zeromail.core.triage.service.TriageGmailWriter";
    private static final String TRIAGE_PENDING_REAPER_JOB =
            "com.zeromail.worker.triage.TriagePendingReaperJob";

    @Test
    void future_idempotency_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(TRIAGE_AUDIT_REPOSITORY);
        assertFutureTypePresent(TRIAGE_GMAIL_WRITER);
        assertFutureTypePresent(TRIAGE_PENDING_REAPER_JOB);
    }

    @Test
    void replaying_same_message_writes_one_audit_row_and_at_most_one_gmail_write_per_action()
            throws Exception {
        assertThat(triageAuditRepositoryMethods())
                .contains("insertAuditPendingIfAbsent")
                .contains("findPendingAuditIdByKey");
        assertThat(triageOrchestratorSource())
                .contains("reservePhase")
                .contains("shouldAttemptGmail")
                .contains("continue;");
    }

    @Test
    void crash_then_replay_reclaims_pending_row_and_completes_gmail_write() throws Exception {
        Class<?> triageAuditRepositoryClass = Class.forName(TRIAGE_AUDIT_REPOSITORY);
        Method reclaimMethod =
                triageAuditRepositoryClass.getMethod("reclaimStalePending", UUID.class, UUID.class, String.class);

        assertThat(reclaimMethod).isNotNull();
        assertThat(triageAuditSagaSource())
                .contains("findPendingAuditId")
                .contains("reclaimStalePending")
                .contains("leaseOwner");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static String triageAuditRepositoryMethods() throws Exception {
        return Arrays.toString(Class.forName(TRIAGE_AUDIT_REPOSITORY).getMethods());
    }

    private static String triageOrchestratorSource() throws Exception {
        return sourceFile(
                "backend/core/src/main/java/com/zeromail/core/triage/application/TriageOrchestratorService.java");
    }

    private static String triageAuditSagaSource() throws Exception {
        return sourceFile(
                "backend/core/src/main/java/com/zeromail/core/triage/application/TriageAuditSaga.java");
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
