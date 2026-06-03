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

    @Test
    void extractDisplayName_unquotes_and_trims_named_address() {
        assertThat(canonicalizer.extractDisplayName("\"John Doe\" <john@example.com>"))
                .contains("John Doe");
        assertThat(canonicalizer.extractDisplayName("Alice Example <Alice@Example.COM>"))
                .contains("Alice Example");
    }

    @Test
    void extractDisplayName_returns_empty_for_bare_address() {
        assertThat(canonicalizer.extractDisplayName("john@example.com")).isEmpty();
        assertThat(canonicalizer.extractDisplayName("<john@example.com>")).isEmpty();
    }

    @Test
    void extractDisplayName_returns_empty_for_null_or_blank_input() {
        assertThat(canonicalizer.extractDisplayName(null)).isEmpty();
        assertThat(canonicalizer.extractDisplayName("")).isEmpty();
        assertThat(canonicalizer.extractDisplayName("   ")).isEmpty();
    }

    @Test
    void extractDisplayName_strips_empty_quoted_name() {
        assertThat(canonicalizer.extractDisplayName("\"\" <john@example.com>")).isEmpty();
    }
}
