package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class GenerateThreadDraftServiceTest {

    private static final String GENERATE_THREAD_DRAFT_SERVICE =
            "com.zeromail.core.draft.usecases.GenerateThreadDraftService";
    private static final String THREAD_REPLY_STATUS_REPOSITORY =
            "com.zeromail.core.thread.persistence.ThreadReplyStatusRepository";

    @Test
    void service_generates_non_empty_body_and_persists_new_draft_state() {
        Class<?> futureType = futureType(GENERATE_THREAD_DRAFT_SERVICE);
        futureType(THREAD_REPLY_STATUS_REPOSITORY);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must consume the LlmGateway save_draft result, save a Gmail draft, and upsert "
                        + "thread_reply_status with the new draftId");
    }

    @Test
    void regeneration_saves_new_draft_before_deleting_old_draft() {
        Class<?> futureType = futureType(GENERATE_THREAD_DRAFT_SERVICE);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must save the replacement draft before deleting the old draft and must log "
                        + "delete failures without propagating them");
    }

    @Test
    void save_draft_failure_leaves_existing_draft_intact() {
        Class<?> futureType = futureType(GENERATE_THREAD_DRAFT_SERVICE);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must not delete or orphan an existing draft when replacement saveDraft fails");
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
