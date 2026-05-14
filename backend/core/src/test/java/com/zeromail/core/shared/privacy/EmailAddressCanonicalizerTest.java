package com.zeromail.core.shared.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressCanonicalizerTest {

    private final EmailAddressCanonicalizer canonicalizer = new EmailAddressCanonicalizer();

    @Test
    void canonicalize_extracts_and_lowercases_display_name_addresses() {
        assertThat(canonicalizer.canonicalize("Boss <Boss@Example.COM>"))
                .isEqualTo("boss@example.com");
    }

    @Test
    void canonicalize_rejects_closing_angle_before_opening_angle() {
        assertThatThrownBy(() -> canonicalizer.canonicalize("foo> <bar@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("senderEmail is malformed");
    }
}
