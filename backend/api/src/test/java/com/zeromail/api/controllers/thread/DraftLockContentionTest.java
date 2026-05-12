package com.zeromail.api.controllers.thread;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class DraftLockContentionTest {

    private static final String GENERATE_THREAD_DRAFT_SERVICE =
            "com.zeromail.core.draft.usecases.GenerateThreadDraftService";
    private static final String ERROR_CODES = "com.zeromail.api.error.ErrorCodes";

    @Test
    void second_concurrent_draft_request_returns_http_409_in_flight_code() {
        futureType(GENERATE_THREAD_DRAFT_SERVICE);
        futureType(ERROR_CODES);

        fail(
                "not implemented: a second POST /api/threads/{gmailThreadId}/draft while the "
                        + "Redis lock is held must return 409 DRAFT_GENERATION_IN_FLIGHT");
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
