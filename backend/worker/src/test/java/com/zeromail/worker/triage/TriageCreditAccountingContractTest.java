package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TriageCreditAccountingContractTest {

    private static final String TRIAGE_ORCHESTRATOR_SERVICE =
            "com.zeromail.core.triage.usecases.TriageOrchestratorService";
    private static final String CREDIT_LEDGER = "com.zeromail.core.billing.service.CreditLedger";
    private static final String TRIAGE_PLATFORM_LLM = "TRIAGE_PLATFORM_LLM";
    private static final String TRIAGE_DETERMINISTIC = "TRIAGE_DETERMINISTIC";

    @Test
    void future_credit_accounting_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ORCHESTRATOR_SERVICE);
        assertFutureTypePresent(CREDIT_LEDGER);
    }

    @Test
    void llm_messages_reserve_once_per_llm_call_and_deterministic_messages_reserve_once()
            throws Exception {
        assertThat(Class.forName("com.zeromail.core.billing.domain.CallSite").getEnumConstants())
                .extracting(Object::toString)
                .contains(TRIAGE_PLATFORM_LLM, TRIAGE_DETERMINISTIC);
        assertThat(orchestratorSource())
                .contains("creditLedger.reserve(tenantId, CallSite.TRIAGE_DETERMINISTIC)")
                .contains("CallSite.TRIAGE_PLATFORM_LLM")
                .contains("evaluateSemanticIntents");
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
