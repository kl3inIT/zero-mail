package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.Test;

class DraftPrivacyLogScrubTest {

    private static final String GENERATE_THREAD_DRAFT_SERVICE =
            "com.zeromail.core.draft.usecases.GenerateThreadDraftService";

    @Test
    void draft_generation_logs_never_include_mail_body_prompt_or_completion_content() {
        futureType();

        List<String> capturedLogLines =
                List.of("event=draft_generation_started tenantId=tenant-1 threadId=thread-1");

        assertThat(String.join("\n", capturedLogLines))
                .doesNotContain("sent-mail-body-sentinel")
                .doesNotContain("draft-body-sentinel")
                .doesNotContain("prompt-sentinel")
                .doesNotContain("completion-sentinel");
        fail(
                "not implemented: replace sentinel-only scaffold with real log capture around "
                        + GENERATE_THREAD_DRAFT_SERVICE);
    }

    private static Class<?> futureType() {
        try {
            return Class.forName(GENERATE_THREAD_DRAFT_SERVICE);
        } catch (ClassNotFoundException classNotFoundException) {
            fail(
                    "not implemented: " + GENERATE_THREAD_DRAFT_SERVICE + " missing",
                    classNotFoundException);
            throw new AssertionError("unreachable");
        }
    }
}
