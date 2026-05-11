package com.zeromail.core.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TopupCodeGeneratorTest {

    private static final String CROCKFORD_EIGHT_CHARACTER_PATTERN = "[0-9A-HJKMNPQRSTVWXYZ]{8}";

    @Test
    void code_is_8_chars_from_crockford_alphabet() {
        TopupCodeGenerator codeGenerator = new TopupCodeGenerator();

        for (int generatedCodeIndex = 0; generatedCodeIndex < 100; generatedCodeIndex++) {
            String generatedCode = codeGenerator.generateUniqueCode(candidateCode -> true, 3);

            assertThat(generatedCode)
                    .hasSize(8)
                    .matches(CROCKFORD_EIGHT_CHARACTER_PATTERN)
                    .doesNotContain("I")
                    .doesNotContain("L")
                    .doesNotContain("O")
                    .doesNotContain("U");
        }
    }

    @Test
    void collision_retry_succeeds_within_three_attempts() {
        TopupCodeGenerator codeGenerator = new TopupCodeGenerator();
        AtomicInteger attemptCounter = new AtomicInteger();

        String acceptedCode =
                codeGenerator.generateUniqueCode(
                        candidateCode -> attemptCounter.incrementAndGet() == 3, 3);

        assertThat(acceptedCode).matches(CROCKFORD_EIGHT_CHARACTER_PATTERN);
        assertThat(attemptCounter).hasValue(3);
    }

    @Test
    void collision_retry_throws_when_exhausted() {
        TopupCodeGenerator codeGenerator = new TopupCodeGenerator();

        assertThatThrownBy(() -> codeGenerator.generateUniqueCode(candidateCode -> false, 3))
                .isInstanceOf(IllegalStateException.class);
    }
}
