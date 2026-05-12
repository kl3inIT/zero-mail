package com.zeromail.core.thread;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class ClassifyThreadReplyStatusServiceTest {

    private static final String CLASSIFY_THREAD_REPLY_STATUS_SERVICE =
            "com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService";
    private static final String THREAD_REPLY_BUCKET =
            "com.zeromail.core.thread.domain.ThreadReplyBucket";

    @Test
    void last_self_message_with_sent_label_classifies_as_awaiting_their_reply() {
        Class<?> futureType = futureType(CLASSIFY_THREAD_REPLY_STATUS_SERVICE);
        futureType(THREAD_REPLY_BUCKET);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must classify a non-auto-reply self/SENT last message as AWAITING_THEIR_REPLY");
    }

    @Test
    void counterparty_last_message_without_draft_classifies_as_to_reply() {
        Class<?> futureType = futureType(CLASSIFY_THREAD_REPLY_STATUS_SERVICE);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must classify a counterparty last message with no Zero-Mail draft as TO_REPLY");
    }

    @Test
    void zero_mail_draft_keeps_thread_to_reply_with_has_draft_true() {
        Class<?> futureType = futureType(CLASSIFY_THREAD_REPLY_STATUS_SERVICE);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must keep draft-bearing threads in TO_REPLY and set hasDraft=true");
    }

    @Test
    void auto_reply_or_bulk_last_message_stays_to_reply() {
        Class<?> futureType = futureType(CLASSIFY_THREAD_REPLY_STATUS_SERVICE);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must treat Auto-Submitted auto-replied and Precedence bulk as TO_REPLY");
    }

    @Test
    void unchanged_last_message_is_idempotent_and_new_inbound_reopens_resolved_row() {
        Class<?> futureType = futureType(CLASSIFY_THREAD_REPLY_STATUS_SERVICE);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must avoid re-upsert for unchanged lastClassifiedMessageId and reopen resolved rows "
                        + "on new inbound activity");
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
