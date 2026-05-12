package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class ThreadingHeaderValidatorTest {

    private static final String THREADING_HEADER_VALIDATOR =
            "com.zeromail.core.triage.usecases.ThreadingHeaderValidator";

    @Test
    void validator_rejects_missing_or_malformed_threading_headers() {
        Class<?> futureType = futureType();

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must reject MIME without In-Reply-To, References, Subject, To, or raw content");
    }

    @Test
    void validator_rejects_thread_id_mismatch_before_drafts_create() {
        Class<?> futureType = futureType();

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must reject a Gmail draft whose Message.threadId mismatches the source thread");
    }

    private static Class<?> futureType() {
        try {
            return Class.forName(THREADING_HEADER_VALIDATOR);
        } catch (ClassNotFoundException classNotFoundException) {
            fail(
                    "not implemented: " + THREADING_HEADER_VALIDATOR + " missing",
                    classNotFoundException);
            throw new AssertionError("unreachable");
        }
    }
}
