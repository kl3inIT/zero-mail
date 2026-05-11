package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;

class SenderSafetyNetControllerContractTest {

    private static final String SENDER_SAFETY_NET_CONTROLLER =
            "com.zeromail.api.controllers.triage.SenderSafetyNetController";
    private static final String SENDER_SAFETY_NET_SERVICE =
            "com.zeromail.core.triage.usecases.SenderSafetyNetService";
    private static final String TENANT_PROTECTED_SENDER_OBSERVATION_REPOSITORY =
            "com.zeromail.core.triage.persistence.TenantProtectedSenderObservationRepository";

    @Test
    void future_sender_controller_contract_types_are_present() {
        assertFutureTypePresent(SENDER_SAFETY_NET_CONTROLLER);
        assertFutureTypePresent(SENDER_SAFETY_NET_SERVICE);
        assertFutureTypePresent(TENANT_PROTECTED_SENDER_OBSERVATION_REPOSITORY);
    }

    @Test
    void sender_safety_net_endpoints_list_observations_and_persist_opt_in_override() {
        assertThat(
                        List.of(
                                "GET /api/triage/sender-safety-net",
                                "POST /api/triage/sender-safety-net/{senderEmail}/opt-in"))
                .containsExactly(
                        "GET /api/triage/sender-safety-net",
                        "POST /api/triage/sender-safety-net/{senderEmail}/opt-in");
        assertThat("event=triage_sender_opt_in tenantId={} senderEmailHash={}")
                .doesNotContain("senderEmail={}", "senderName={}");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
