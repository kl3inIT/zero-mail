package com.zeromail.core.rules.usecases;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ActionIntentJsonValidatorTest {

    private final ActionIntentJsonValidator validator = new ActionIntentJsonValidator();

    @Test
    void accepts_expanded_phase_8_1_action_intent_shapes() {
        assertThatCode(
                        () ->
                                validator.validateActionIntentsJson(
                                        """
                    [
                      {"type":"label","labelName":"Finance"},
                      {"type":"archive"},
                      {"type":"save_draft","instruction":"Draft a short reply asking for a PDF invoice"},
                      {"type":"mark_read"},
                      {"type":"star"},
                      {"type":"add_to_digest"},
                      {"type":"mark_spam"},
                      {"type":"send_reply","instruction":"Send a short thank-you reply"},
                      {"type":"forward_email","recipients":["ops@example.com"],"instruction":"Forward with a short context note"},
                      {"type":"send_email","to":["founder@example.com"],"subject":"Daily investor update","body":"Here is the investor update."}
                    ]
                    """))
                .doesNotThrowAnyException();
    }

    @Test
    void rejects_unknown_action_type_and_unknown_action_fields() {
        assertThatThrownBy(
                        () ->
                                validator.validateActionIntentsJson(
                                        """
                    [{"type":"webhook","url":"https://example.com/hook"}]
                    """))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(
                        () ->
                                validator.validateActionIntentsJson(
                                        """
                    [{"type":"send_reply","instruction":"Reply politely","prompt":"hidden"}]
                    """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_outbound_actions_without_required_payload_fields() {
        assertThatThrownBy(() -> validator.validateActionIntentsJson("[{\"type\":\"send_reply\"}]"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                validator.validateActionIntentsJson(
                                        """
                    [{"type":"forward_email","instruction":"Forward this"}]
                    """))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                validator.validateActionIntentsJson(
                                        """
                    [{"type":"send_email","to":["founder@example.com"],"subject":"Missing body"}]
                    """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_invalid_recipient_syntax_shape() {
        assertThatThrownBy(
                        () ->
                                validator.validateActionIntentsJson(
                                        """
                    [{"type":"forward_email","recipients":["not an email"],"instruction":"Forward"}]
                    """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_gmail_read_body_and_snippet_source_fields() {
        assertThatThrownBy(
                        () ->
                                validator.validateActionIntentsJson(
                                        """
                    [{"type":"send_reply","instruction":"Reply politely","gmailReadBody":"PRIVATE_MAIL_BODY"}]
                    """))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                        () ->
                                validator.validateActionIntentsJson(
                                        """
                    [{"type":"send_email","to":["founder@example.com"],"subject":"x","body":"draft","snippet":"PRIVATE_SNIPPET"}]
                    """))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
