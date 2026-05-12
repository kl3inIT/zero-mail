package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.Test;

class TriageAuditControllerContractTest {

    private static final String TRIAGE_AUDIT_CONTROLLER =
            "com.zeromail.api.controllers.triage.TriageAuditController";
    private static final String AUDIT_ENTRY_RESPONSE =
            "com.zeromail.api.dto.triage.AuditEntryResponse";
    private static final String AUDIT_LIST_RESPONSE =
            "com.zeromail.api.dto.triage.AuditListResponse";

    @Test
    void audit_list_endpoint_returns_items_and_next_cursor_contract() {
        futureType(TRIAGE_AUDIT_CONTROLLER);
        futureType(AUDIT_ENTRY_RESPONSE);
        futureType(AUDIT_LIST_RESPONSE);

        assertThat("/api/triage/audit").isEqualTo("/api/triage/audit");
        assertThat(
                        List.of(
                                "auditId",
                                "gmailThreadId",
                                "gmailMessageId",
                                "ruleName",
                                "action",
                                "reason",
                                "decisionState",
                                "createdAt",
                                "draftId"))
                .containsExactly(
                        "auditId",
                        "gmailThreadId",
                        "gmailMessageId",
                        "ruleName",
                        "action",
                        "reason",
                        "decisionState",
                        "createdAt",
                        "draftId");

        fail("not implemented: GET /api/triage/audit must return { items, nextCursor }");
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
