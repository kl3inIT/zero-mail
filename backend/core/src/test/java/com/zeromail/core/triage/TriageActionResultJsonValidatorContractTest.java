package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.shared.privacy.EmailAddressCanonicalizer;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.exception.TriageSafetyViolationException;
import com.zeromail.core.triage.usecases.SenderEmailCanonicalizer;
import com.zeromail.core.triage.usecases.TriageActionArgsCanonicalizer;
import com.zeromail.core.triage.usecases.TriageActionResultJsonValidator;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class TriageActionResultJsonValidatorContractTest {

    private static final String TRIAGE_ACTION_RESULT =
            "com.zeromail.core.triage.domain.TriageActionResult";
    private static final String TRIAGE_ACTION_RESULT_JSON_VALIDATOR =
            "com.zeromail.core.triage.usecases.TriageActionResultJsonValidator";
    private static final String TRIAGE_ACTION_ARGS_CANONICALIZER =
            "com.zeromail.core.triage.usecases.TriageActionArgsCanonicalizer";
    private static final String TRIAGE_DECISION = "com.zeromail.core.triage.domain.TriageDecision";

    @Test
    void future_action_json_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_ACTION_RESULT);
        assertFutureTypePresent(TRIAGE_ACTION_RESULT_JSON_VALIDATOR);
        assertFutureTypePresent(TRIAGE_ACTION_ARGS_CANONICALIZER);
        assertFutureTypePresent(TRIAGE_DECISION);
    }

    @Test
    void unknown_discriminator_fails_loudly_with_no_silent_noop() {
        TriageActionResultJsonValidator validator = new TriageActionResultJsonValidator();

        assertThatThrownBy(
                        () ->
                                validator.validateActionArgsJson(
                                        """
                {"type":"send","messageId":"unsafe"}
                """))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void unknown_fields_are_rejected_per_action_type_on_write() {
        TriageActionResultJsonValidator validator = new TriageActionResultJsonValidator();

        assertThatThrownBy(
                        () ->
                                validator.validateActionArgsJson(
                                        """
                {"type":"archive","extra":"not-allowed"}
                """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void save_draft_hash_is_stable_before_and_after_gmail_returns_draft_id() {
        TriageActionArgsCanonicalizer canonicalizer = new TriageActionArgsCanonicalizer();

        byte[] preWriteHash =
                canonicalizer.canonicalHash(
                        """
                {"type":"save_draft","instruction":"draft politely","draftId":null,"threadId":"thread-1"}
                """);
        byte[] postWriteHash =
                canonicalizer.canonicalHash(
                        """
                {"threadId":"thread-1","draftId":"draft-1","instruction":"draft politely","type":"save_draft"}
                """);

        assertThat(postWriteHash).isEqualTo(preWriteHash).hasSize(32);
    }

    @Test
    void validator_serializes_persisted_action_json_without_jackson_type_metadata() {
        TriageActionResultJsonValidator validator = new TriageActionResultJsonValidator();

        String serializedJson =
                validator.toJson(new TriageActionResult.Label("Label_123", "Finance"));

        assertThat(serializedJson)
                .contains(
                        "\"type\":\"label\"",
                        "\"labelId\":\"Label_123\"",
                        "\"labelName\":\"Finance\"")
                .doesNotContain("@class", "JsonTypeInfo");
        assertThatCode(() -> validator.validateActionArgsJson(serializedJson))
                .doesNotThrowAnyException();
    }

    @Test
    void outbound_action_json_allows_user_authored_body_but_rejects_gmail_read_sources() {
        TriageActionResultJsonValidator validator = new TriageActionResultJsonValidator();

        String serializedJson =
                validator.toJson(
                        new TriageActionResult.SendEmail(
                                java.util.List.of("safe@example.com"),
                                java.util.List.of(),
                                java.util.List.of(),
                                "Status",
                                "USER_AUTHORED_DRAFT_BODY"));

        assertThat(serializedJson)
                .contains("\"type\":\"send_email\"", "\"body\":\"USER_AUTHORED_DRAFT_BODY\"")
                .doesNotContain("draftBody", "gmailReadBody", "snippet", "prompt", "completion");
        assertThatCode(() -> validator.validateActionArgsJson(serializedJson))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                validator.validateActionArgsJson(
                                        """
                {"type":"send_email","to":["safe@example.com"],"subject":"Status","body":"ok","gmailReadBody":"PRIVATE"}
                """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outbound_action_hash_is_stable_across_json_field_order() {
        TriageActionArgsCanonicalizer canonicalizer = new TriageActionArgsCanonicalizer();

        byte[] firstHash =
                canonicalizer.canonicalHash(
                        """
                {"type":"send_email","to":["safe@example.com"],"cc":[],"bcc":[],"subject":"Status","body":"Body"}
                """);
        byte[] secondHash =
                canonicalizer.canonicalHash(
                        """
                {"body":"Body","subject":"Status","bcc":[],"cc":[],"to":["safe@example.com"],"type":"send_email"}
                """);

        assertThat(secondHash).isEqualTo(firstHash).hasSize(32);
    }

    @Test
    void persisted_outbound_action_json_rejects_invalid_recipients_before_gmail_execution() {
        TriageActionResultJsonValidator validator = new TriageActionResultJsonValidator();
        TriageActionArgsCanonicalizer canonicalizer = new TriageActionArgsCanonicalizer();

        assertThatThrownBy(
                        () ->
                                validator.validateActionArgsJson(
                                        """
                {"type":"forward_email","recipients":["not-an-email"],"body":"Body"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid email address");
        assertThatThrownBy(
                        () ->
                                validator.validateActionArgsJson(
                                        """
                {"type":"send_email","to":["safe@example.com"],"cc":["  "],"subject":"Status","body":"Body"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank values");
        assertThatThrownBy(
                        () ->
                                validator.validateActionArgsJson(
                                        """
                {
                  "type":"send_email",
                  "to":[
                    "one@example.com",
                    "two@example.com",
                    "three@example.com",
                    "four@example.com",
                    "five@example.com",
                    "six@example.com",
                    "seven@example.com",
                    "eight@example.com",
                    "nine@example.com",
                    "ten@example.com",
                    "eleven@example.com"
                  ],
                  "subject":"Status",
                  "body":"Body"
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too many recipients");
        assertThatThrownBy(
                        () ->
                                canonicalizer.canonicalJson(
                                        """
                {"type":"send_email","to":["safe@example.com"],"bcc":["bad-recipient"],"subject":"Status","body":"Body"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid email address");
        assertThatThrownBy(
                        () ->
                                new TriageActionResult.ForwardEmail(
                                        List.of("bad-recipient"), "Forward this"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid email address");
    }

    @Test
    void sender_email_canonicalizer_normalizes_hashes_and_quotes_sender_addresses() {
        SenderEmailCanonicalizer canonicalizer =
                new SenderEmailCanonicalizer(new EmailAddressCanonicalizer());

        String canonicalEmail = canonicalizer.canonicalize("Boss <Boss@Example.COM> ");

        assertThat(canonicalEmail).isEqualTo("boss@example.com");
        assertThat(canonicalizer.redisCacheKeyComponent(canonicalEmail))
                .hasSize(64)
                .doesNotContain("boss", "example");
        assertThat(canonicalizer.gmailSearchToken(canonicalEmail))
                .isEqualTo("\"boss@example.com\"");
        assertThatThrownBy(() -> canonicalizer.canonicalize("not-an-address"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void triage_decision_fails_loudly_for_unknown_ids() {
        assertThat(TriageDecision.fromId("APPLIED")).isEqualTo(TriageDecision.APPLIED);
        assertThat(TriageDecision.values())
                .extracting(TriageDecision::id)
                .containsExactly(
                        "PENDING",
                        "APPLIED",
                        "REJECTED_BY_SAFETY_NET",
                        "REJECTED_BY_SAFETY_POLICY",
                        "FAILED",
                        "REVERT_PENDING",
                        "REVERTED");
        assertThatThrownBy(() -> TriageDecision.fromId("NOPE"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void triage_safety_violation_exception_has_only_no_arg_constructor() {
        assertThat(TriageSafetyViolationException.class.getConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount()).isZero());
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }
}
