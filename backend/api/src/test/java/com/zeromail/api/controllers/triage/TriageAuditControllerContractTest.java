package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.zeromail.api.dto.triage.AuditEntryResponse;
import com.zeromail.api.dto.triage.AuditListResponse;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

class TriageAuditControllerContractTest {

    private static final String TRIAGE_AUDIT_CONTROLLER =
            "com.zeromail.api.controllers.triage.TriageAuditController";
    private static final String AUDIT_ENTRY_RESPONSE =
            "com.zeromail.api.dto.triage.AuditEntryResponse";
    private static final String AUDIT_LIST_RESPONSE =
            "com.zeromail.api.dto.triage.AuditListResponse";

    @Test
    void audit_list_endpoint_returns_items_and_next_cursor_contract() throws Exception {
        assertFutureTypePresent(TRIAGE_AUDIT_CONTROLLER);
        assertFutureTypePresent(AUDIT_ENTRY_RESPONSE);
        assertFutureTypePresent(AUDIT_LIST_RESPONSE);

        GetMapping getMapping =
                TriageAuditController.class
                        .getMethod(
                                "list",
                                int.class,
                                String.class,
                                String.class,
                                java.time.Instant.class,
                                java.time.Instant.class)
                        .getAnnotation(GetMapping.class);
        assertThat(getMapping.value()).containsExactly("/api/triage/audit");
        assertThat(
                        List.of(AuditEntryResponse.class.getRecordComponents()).stream()
                                .map(RecordComponent::getName)
                                .toList())
                .containsExactly(
                        "auditId",
                        "gmailThreadId",
                        "gmailMessageId",
                        "subject",
                        "senderEmail",
                        "ruleName",
                        "action",
                        "reason",
                        "decisionState",
                        "createdAt",
                        "undoableUntil",
                        "draftId");
        assertThat(
                        List.of(AuditListResponse.class.getRecordComponents()).stream()
                                .map(RecordComponent::getName)
                                .toList())
                .containsExactly("items", "nextCursor");
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
