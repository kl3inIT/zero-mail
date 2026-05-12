package com.zeromail.api.controllers.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class ThreadDraftControllerContractTest {

    private static final String THREAD_DRAFT_CONTROLLER =
            "com.zeromail.api.controllers.thread.ThreadDraftController";
    private static final String NEEDS_REPLY_INBOX_CONTROLLER =
            "com.zeromail.api.controllers.thread.NeedsReplyInboxController";
    private static final String THREAD_DRAFT_RESPONSE =
            "com.zeromail.api.dto.thread.ThreadDraftResponse";
    private static final String NEEDS_REPLY_LIST_RESPONSE =
            "com.zeromail.api.dto.thread.NeedsReplyListResponse";

    @Test
    void draft_endpoint_returns_no_body_and_links_to_gmail() {
        futureType(THREAD_DRAFT_CONTROLLER);
        futureType(THREAD_DRAFT_RESPONSE);

        assertThat("/api/threads/{gmailThreadId}/draft")
                .isEqualTo("/api/threads/{gmailThreadId}/draft");
        fail(
                "not implemented: POST /api/threads/{gmailThreadId}/draft must return draftId, "
                        + "gmailThreadId, status, and openInGmailUrl with no draft body field");
    }

    @Test
    void needs_reply_endpoint_uses_hyphenated_public_bucket_slugs() {
        futureType(NEEDS_REPLY_INBOX_CONTROLLER);
        futureType(NEEDS_REPLY_LIST_RESPONSE);

        assertThat("to-reply").isEqualTo("to-reply");
        assertThat("awaiting-their-reply").isEqualTo("awaiting-their-reply");
        fail(
                "not implemented: GET /api/threads?bucket=to-reply must return cursor-paginated rows "
                        + "and toReplyCount");
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
