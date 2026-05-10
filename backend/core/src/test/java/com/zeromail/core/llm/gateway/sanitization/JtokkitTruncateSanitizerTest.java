package com.zeromail.core.llm.gateway.sanitization;

import static org.assertj.core.api.Assertions.assertThat;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.zeromail.core.llm.application.SanitizationContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JtokkitTruncateSanitizerTest {

    private final EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
    private final Encoding cl100kBaseEncoding = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
    private final JtokkitTruncateSanitizer sanitizer = new JtokkitTruncateSanitizer(encodingRegistry);

    @Test
    void truncates_long_input() {
        String longInput = "token ".repeat(10000) + "\uD83C\uDF55";

        SanitizationContext sanitizedContext = sanitizer.apply(SanitizationContext.initial(longInput));

        assertThat(sanitizedContext.tokenCount()).isLessThanOrEqualTo(JtokkitTruncateSanitizer.HARD_CAP_TOKENS);
        assertThat(sanitizedContext.truncated()).isTrue();
        assertThat(new String(sanitizedContext.content().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .isEqualTo(sanitizedContext.content());
        assertThat(sanitizedContext.content()).doesNotContain("\uFFFD");
    }

    @Test
    void under_budget_passes_through() {
        String underBudgetInput = "hello ".repeat(100);

        SanitizationContext sanitizedContext = sanitizer.apply(SanitizationContext.initial(underBudgetInput));

        assertThat(sanitizedContext.truncated()).isFalse();
        assertThat(sanitizedContext.content()).isEqualTo(underBudgetInput);
    }

    @Test
    void tokenCount_populated_in_metadata() {
        String input = "hello world";

        SanitizationContext sanitizedContext = sanitizer.apply(SanitizationContext.initial(input));

        assertThat(sanitizedContext.tokenCount()).isEqualTo(cl100kBaseEncoding.encode(input).size());
    }
}
