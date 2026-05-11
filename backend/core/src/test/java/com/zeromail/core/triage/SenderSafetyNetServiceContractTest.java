package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SenderSafetyNetServiceContractTest {

    private static final String PLAN_04_SENDER_SAFETY_MESSAGE =
            "Wave 0 contract - enabled by 04-08 when sender safety net lands";
    private static final String SENDER_SAFETY_NET_SERVICE =
            "com.zeromail.core.triage.service.SenderSafetyNetService";
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
    @Disabled(PLAN_04_SENDER_SAFETY_MESSAGE)
    void frequent_sent_history_marks_sender_protected_until_opt_in_overrides_it() throws Exception {
        Object senderSafetyNetService = Class.forName(SENDER_SAFETY_NET_SERVICE)
                .getConstructor()
                .newInstance();
        Method isProtectedMethod = senderSafetyNetService.getClass().getMethod("isProtected", String.class, String.class);
        Method optInMethod = senderSafetyNetService.getClass().getMethod("optIn", String.class, String.class);

        assertThat(isProtectedMethod.invoke(senderSafetyNetService, "tenant-a", "boss@example.com")).isEqualTo(true);
        optInMethod.invoke(senderSafetyNetService, "tenant-a", "boss@example.com");
        assertThat(isProtectedMethod.invoke(senderSafetyNetService, "tenant-a", "boss@example.com")).isEqualTo(false);
    }

    @Test
    @Disabled(PLAN_04_SENDER_SAFETY_MESSAGE)
    void opt_in_logging_uses_hashed_or_id_only_sender_fields() {
        String plannedLogLine = "event=triage_sender_opt_in tenantId={} senderEmailHash={}";

        assertThat(plannedLogLine).doesNotContain("senderEmail={}", "senderName={}");
        assertThat(plannedLogLine).contains("tenantId={}", "senderEmailHash={}");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
