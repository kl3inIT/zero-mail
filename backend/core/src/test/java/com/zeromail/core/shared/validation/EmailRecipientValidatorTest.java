package com.zeromail.core.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EmailRecipientValidatorTest {

    @Test
    void accepts_bare_valid_address() {
        assertThat(EmailRecipientValidator.required(List.of("dat@example.com"), "to"))
                .containsExactly("dat@example.com");
    }

    @Test
    void normalizes_display_name_form_to_bare_address() {
        // The LLM rule compiler legitimately emits a valid recipient wrapped in a display name.
        // Previously this was rejected as "invalid email address" and the forward/send rule could
        // not be created even though the address is perfectly valid.
        assertThat(EmailRecipientValidator.required(List.of("Dat Nguyen <dat@example.com>"), "to"))
                .containsExactly("dat@example.com");
    }

    @Test
    void splits_a_single_comma_joined_string_into_multiple_addresses() {
        assertThat(
                        EmailRecipientValidator.required(
                                List.of("a@example.com, b@example.com"), "recipients"))
                .containsExactly("a@example.com", "b@example.com");
    }

    @Test
    void rejects_a_bare_name_without_an_at_sign() {
        assertThatThrownBy(() -> EmailRecipientValidator.required(List.of("Dat Nguyen"), "to"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains invalid email address");
    }

    @Test
    void rejects_free_text_that_is_not_an_email() {
        assertThatThrownBy(() -> EmailRecipientValidator.required(List.of("not an email"), "to"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains invalid email address");
    }

    @Test
    void rejects_blank_recipient() {
        assertThatThrownBy(() -> EmailRecipientValidator.required(List.of("   "), "to"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain blank values");
    }

    @Test
    void required_rejects_empty_list() {
        assertThatThrownBy(() -> EmailRecipientValidator.required(List.of(), "to"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void rejects_more_than_ten_addresses_after_expansion() {
        String elevenJoined =
                IntStream.range(0, 11)
                        .mapToObj(index -> "user" + index + "@example.com")
                        .reduce((left, right) -> left + ", " + right)
                        .orElseThrow();
        assertThatThrownBy(
                        () -> EmailRecipientValidator.required(List.of(elevenJoined), "recipients"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has too many recipients");
    }

    @Test
    void optional_returns_empty_for_null() {
        assertThat(EmailRecipientValidator.optional(null, "cc")).isEmpty();
    }
}
