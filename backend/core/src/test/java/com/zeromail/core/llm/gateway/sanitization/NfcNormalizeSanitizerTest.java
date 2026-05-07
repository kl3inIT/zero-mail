package com.zeromail.core.llm.gateway.sanitization;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.model.SanitizationContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NfcNormalizeSanitizerTest {

    private final NfcNormalizeSanitizer sanitizer = new NfcNormalizeSanitizer();

    @Test
    void decomposed_and_composed_forms_match() {
        SanitizationContext decomposedContext = sanitizer.apply(SanitizationContext.initial("n\u0303"));
        SanitizationContext composedContext = sanitizer.apply(SanitizationContext.initial("\u00f1"));

        assertThat(decomposedContext.content().getBytes(StandardCharsets.UTF_8))
                .isEqualTo(composedContext.content().getBytes(StandardCharsets.UTF_8));
    }
}
