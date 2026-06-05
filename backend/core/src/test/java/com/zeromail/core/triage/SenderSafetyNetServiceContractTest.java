package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SenderSafetyNetServiceContractTest {

    private static final String SENDER_SAFETY_NET_SERVICE =
            "com.zeromail.core.triage.usecases.SenderSafetyNetService";
    private static final String TENANT_SENDER_OPT_IN_ENTITY =
            "com.zeromail.core.triage.persistence.TenantSenderOptInEntity";
    private static final String TENANT_SENDER_OPT_IN_REPOSITORY =
            "com.zeromail.core.triage.persistence.TenantSenderOptInRepository";
    private static final String TENANT_PROTECTED_SENDER_OBSERVATION_ENTITY =
            "com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity";
    private static final String TENANT_PROTECTED_SENDER_OBSERVATION_REPOSITORY =
            "com.zeromail.core.triage.persistence.TenantProtectedSenderObservationRepository";

    @Test
    void future_sender_safety_contract_types_are_present() {
        assertFutureTypePresent(SENDER_SAFETY_NET_SERVICE);
        assertFutureTypePresent(TENANT_SENDER_OPT_IN_ENTITY);
        assertFutureTypePresent(TENANT_SENDER_OPT_IN_REPOSITORY);
        assertFutureTypePresent(TENANT_PROTECTED_SENDER_OBSERVATION_ENTITY);
        assertFutureTypePresent(TENANT_PROTECTED_SENDER_OBSERVATION_REPOSITORY);
    }

    @Test
    void protected_set_is_user_managed_only_with_no_gmail_auto_derivation() throws Exception {
        Class<?> senderSafetyNetServiceClass = Class.forName(SENDER_SAFETY_NET_SERVICE);
        Method isProtectedMethod =
                senderSafetyNetServiceClass.getMethod("isProtected", UUID.class, String.class);
        Method optInMethod =
                senderSafetyNetServiceClass.getMethod("optInSender", UUID.class, String.class);

        assertThat(isProtectedMethod).isNotNull();
        assertThat(optInMethod).isNotNull();
        assertThat(senderSafetyNetSource())
                // Protection comes only from the user-managed list (created_by_user rows), with the
                // opt-in exemption still honoured.
                .contains("existsByTenantIdAndSenderEmail")
                .contains("isCreatedByUser")
                // The former Gmail sent-history auto-derivation (≥3 sent in 90d, fail-safe) is
                // gone.
                .doesNotContain("newer_than:90d")
                .doesNotContain("setMaxResults(3L)")
                .doesNotContain("upsertProtectedObservation")
                .doesNotContain("queryGmailSentHistory");
    }

    @Test
    void opt_in_logging_uses_hashed_or_id_only_sender_fields() throws Exception {
        assertThat(senderSafetyNetSource())
                .contains("event=triage_sender_opt_in tenantId={} senderEmailHash={}")
                .contains("redisCacheKeyComponent")
                .doesNotContain("senderEmail={}", "senderName={}");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    private static String senderSafetyNetSource() throws Exception {
        return sourceFile(
                "backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderSafetyNetService.java");
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
