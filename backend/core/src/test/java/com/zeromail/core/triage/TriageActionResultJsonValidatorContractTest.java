package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TriageActionResultJsonValidatorContractTest {

    private static final String PLAN_04_ACTION_JSON_MESSAGE =
            "Wave 0 contract - enabled by 04-02 when triage action JSON types land";
    private static final String TRIAGE_ACTION_RESULT =
            "com.zeromail.core.triage.domain.TriageActionResult";
    private static final String TRIAGE_ACTION_RESULT_JSON_VALIDATOR =
            "com.zeromail.core.triage.domain.TriageActionResultJsonValidator";
    private static final String TRIAGE_ACTION_ARGS_CANONICALIZER =
            "com.zeromail.core.triage.domain.TriageActionArgsCanonicalizer";
    private static final String TRIAGE_DECISION =
            "com.zeromail.core.triage.domain.TriageDecision";

    @Test
    void future_action_json_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ACTION_RESULT);
        assertFutureTypePresent(TRIAGE_ACTION_RESULT_JSON_VALIDATOR);
        assertFutureTypePresent(TRIAGE_ACTION_ARGS_CANONICALIZER);
        assertFutureTypePresent(TRIAGE_DECISION);
    }

    @Test
    @Disabled(PLAN_04_ACTION_JSON_MESSAGE)
    void unknown_discriminator_fails_loudly_with_no_silent_noop() throws Exception {
        Object validator = Class.forName(TRIAGE_ACTION_RESULT_JSON_VALIDATOR).getConstructor().newInstance();
        Method validateMethod = validator.getClass().getMethod("validateActionArgsJson", String.class);

        assertThatThrownBy(() -> validateMethod.invoke(validator, """
                {"type":"send","messageId":"unsafe"}
                """))
                .hasRootCauseInstanceOf(NoSuchElementException.class);
    }

    @Test
    @Disabled(PLAN_04_ACTION_JSON_MESSAGE)
    void unknown_fields_are_rejected_per_action_type_on_write() throws Exception {
        Object validator = Class.forName(TRIAGE_ACTION_RESULT_JSON_VALIDATOR).getConstructor().newInstance();
        Method validateMethod = validator.getClass().getMethod("validateActionArgsJson", String.class);

        assertThatThrownBy(() -> validateMethod.invoke(validator, """
                {"type":"archive","extra":"not-allowed"}
                """))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Disabled(PLAN_04_ACTION_JSON_MESSAGE)
    void save_draft_hash_is_stable_before_and_after_gmail_returns_draft_id() throws Exception {
        Object canonicalizer = Class.forName(TRIAGE_ACTION_ARGS_CANONICALIZER)
                .getConstructor()
                .newInstance();
        Method canonicalHashMethod = canonicalizer.getClass().getMethod("canonicalHash", String.class);

        Object preWriteHash = canonicalHashMethod.invoke(canonicalizer, """
                {"type":"save_draft","instruction":"draft politely","draftId":null,"threadId":"thread-1"}
                """);
        Object postWriteHash = canonicalHashMethod.invoke(canonicalizer, """
                {"threadId":"thread-1","draftId":"draft-1","instruction":"draft politely","type":"save_draft"}
                """);

        assertThat(postWriteHash).isEqualTo(preWriteHash);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
