package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class TriageAuditSagaDraftThreadingTest {

    private static final String TRIAGE_AUDIT_SAGA =
            "com.zeromail.core.triage.usecases.TriageAuditSaga";
    private static final String REPLY_HEADERS = "com.zeromail.core.triage.domain.ReplyHeaders";

    @Test
    void save_draft_branch_passes_reply_headers_to_gmail_writer() {
        Class<?> sagaType = futureType(TRIAGE_AUDIT_SAGA);
        futureType(REPLY_HEADERS);

        fail(
                "not implemented: "
                        + sagaType.getName()
                        + " gmailWritePhase SaveDraft branch must pass ReplyHeaders from "
                        + "TriageAuditCommand into TriageGmailWriter.saveDraft");
    }

    @Test
    void missing_message_id_records_failed_write_and_creates_no_draft() {
        Class<?> sagaType = futureType(TRIAGE_AUDIT_SAGA);

        fail(
                "not implemented: "
                        + sagaType.getName()
                        + " must record GmailWriteResult.failed and skip draft creation when Message-ID is missing");
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
