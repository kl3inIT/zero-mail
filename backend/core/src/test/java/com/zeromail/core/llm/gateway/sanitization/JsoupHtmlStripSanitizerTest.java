package com.zeromail.core.llm.gateway.sanitization;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.model.SanitizationContext;
import org.junit.jupiter.api.Test;

class JsoupHtmlStripSanitizerTest {

    private final JsoupHtmlStripSanitizer sanitizer = new JsoupHtmlStripSanitizer();

    @Test
    void strips_html_tags_and_scripts() {
        SanitizationContext sanitizedContext = sanitizer.apply(
                SanitizationContext.initial("<script>alert(1)</script><p>hi</p>"));

        assertThat(sanitizedContext.content()).isEqualTo("hi");
    }

    @Test
    void preserves_plain_text() {
        SanitizationContext sanitizedContext = sanitizer.apply(
                SanitizationContext.initial("Plain text without HTML"));

        assertThat(sanitizedContext.content()).isEqualTo("Plain text without HTML");
    }
}
