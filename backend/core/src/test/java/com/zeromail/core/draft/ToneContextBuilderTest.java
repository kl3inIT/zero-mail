package com.zeromail.core.draft;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class ToneContextBuilderTest {

    private static final String TONE_CONTEXT_BUILDER =
            "com.zeromail.core.draft.usecases.ToneContextBuilder";

    @Test
    void builder_strips_quotes_and_signatures_then_sanitizes_each_snippet() {
        Class<?> futureType = futureType();

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must strip quoted replies and signatures, then pass each snippet through "
                        + "SanitizationPipeline");
    }

    @Test
    void token_budget_or_partial_gmail_failure_degrades_to_descriptors_only() {
        Class<?> futureType = futureType();

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must return descriptor-only tone context after token-budget or partial Gmail failures");
    }

    @Test
    void snippet_content_is_not_persisted_after_context_build() {
        Class<?> futureType = futureType();

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must keep raw tone snippets in-request only and never persist them");
    }

    private static Class<?> futureType() {
        try {
            return Class.forName(TONE_CONTEXT_BUILDER);
        } catch (ClassNotFoundException classNotFoundException) {
            fail("not implemented: " + TONE_CONTEXT_BUILDER + " missing", classNotFoundException);
            throw new AssertionError("unreachable");
        }
    }
}
