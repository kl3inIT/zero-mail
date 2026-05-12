package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class ReplyMimeBuildTest {

    private static final String MIME_MESSAGE = "jakarta.mail.internet.MimeMessage";
    private static final String REPLY_MIME_BUILDER =
            "com.zeromail.core.draft.usecases.ReplyMimeBuilder";

    @Test
    void jakarta_mail_mime_message_is_available_at_test_runtime() {
        assertThatCode(() -> Class.forName(MIME_MESSAGE)).doesNotThrowAnyException();
    }

    @Test
    void reply_mime_builder_sets_threading_headers_and_thread_id() {
        Class<?> futureType = futureType(REPLY_MIME_BUILDER);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must build a base64url-no-padding Gmail draft raw message with "
                        + "In-Reply-To, References, a single Re: subject prefix, To, and threadId");
    }

    @Test
    void reply_mime_builder_fails_closed_without_message_id() {
        Class<?> futureType = futureType(REPLY_MIME_BUILDER);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must reject missing Message-ID before Gmail drafts.create");
    }

    @Test
    void localized_reply_prefixes_are_documented_as_cosmetic_double_prefixes() {
        Class<?> futureType = futureType(REPLY_MIME_BUILDER);

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must document and assert v1 behavior for AW:, SV:, and RV: subjects");
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
