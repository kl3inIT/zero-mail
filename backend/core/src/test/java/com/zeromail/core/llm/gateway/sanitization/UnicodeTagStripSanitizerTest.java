package com.zeromail.core.llm.gateway.sanitization;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.application.SanitizationContext;
import org.junit.jupiter.api.Test;

class UnicodeTagStripSanitizerTest {

    private final UnicodeTagStripSanitizer sanitizer = new UnicodeTagStripSanitizer();

    @Test
    void strips_tag_chars() {
        String hiddenTagCharacter = new String(Character.toChars(0xE0041));

        SanitizationContext sanitizedContext = sanitizer.apply(
                SanitizationContext.initial("Visible" + hiddenTagCharacter + " body"));

        assertThat(sanitizedContext.content()).isEqualTo("Visible body");
        assertThat(sanitizedContext.content().codePoints())
                .noneMatch(codePoint -> codePoint >= 0xE0000 && codePoint <= 0xE007F);
    }

    @Test
    void strips_zero_width_and_rtl() {
        SanitizationContext sanitizedContext = sanitizer.apply(
                SanitizationContext.initial("Please\u200D confirm\u200F receipt\u202E"));

        assertThat(sanitizedContext.content()).isEqualTo("Please confirm receipt");
        assertThat(sanitizedContext.content())
                .doesNotContain("\u200B", "\u200C", "\u200D", "\uFEFF", "\u202A", "\u202B",
                        "\u202C", "\u202D", "\u202E", "\u2066", "\u2067", "\u2068", "\u2069");
    }
}
