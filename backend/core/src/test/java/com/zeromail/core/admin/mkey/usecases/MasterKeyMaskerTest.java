package com.zeromail.core.admin.mkey.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.domain.LlmProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MasterKeyMaskerTest {

    @Test
    void masks_openai_shape_without_exposing_first_characters() {
        byte[] plaintext = ("sk-" + "proj-abcdef1234").getBytes(StandardCharsets.UTF_8);

        assertThat(MasterKeyMasker.mask(plaintext, LlmProvider.OPENAI))
                .isEqualTo("sk-****1234")
                .doesNotContain("proj");
    }

    @Test
    void masks_anthropic_shape_with_anthropic_prefix_only() {
        byte[] plaintext = ("sk-ant-" + "abcdef1234").getBytes(StandardCharsets.UTF_8);

        assertThat(MasterKeyMasker.mask(plaintext, LlmProvider.ANTHROPIC))
                .isEqualTo("sk-ant-****1234")
                .doesNotContain("abcdef");
    }

    @Test
    void masks_google_shape_with_last_four_only() {
        byte[] plaintext = ("AIza" + "abcdef1234").getBytes(StandardCharsets.UTF_8);

        assertThat(MasterKeyMasker.mask(plaintext, LlmProvider.GOOGLE))
                .isEqualTo("AIza****1234")
                .doesNotContain("abcdef");
    }
}
