package com.zeromail.core.admin.mkey.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.domain.KeyFormat;
import com.zeromail.core.llm.domain.LlmProvider;
import org.junit.jupiter.api.Test;

class LlmProviderTest {

    @Test
    void spring_ai_built_in_providers_accept_only_their_native_key_format() {
        assertThat(LlmProvider.OPENAI.acceptsKeyFormat(KeyFormat.OPENAI_FORMAT)).isTrue();
        assertThat(LlmProvider.ANTHROPIC.acceptsKeyFormat(KeyFormat.ANTHROPIC_FORMAT)).isTrue();
        assertThat(LlmProvider.GOOGLE.acceptsKeyFormat(KeyFormat.GOOGLE_FORMAT)).isTrue();
        assertThat(LlmProvider.DEEPSEEK.acceptsKeyFormat(KeyFormat.OPENAI_FORMAT)).isTrue();

        assertThat(LlmProvider.OPENAI.acceptsKeyFormat(KeyFormat.ANTHROPIC_FORMAT)).isFalse();
        assertThat(LlmProvider.ANTHROPIC.acceptsKeyFormat(KeyFormat.OPENAI_FORMAT)).isFalse();
        assertThat(LlmProvider.GOOGLE.acceptsKeyFormat(KeyFormat.OPENAI_FORMAT)).isFalse();
    }

    @Test
    void compatibility_gateways_accept_openai_and_anthropic_protocols() {
        assertThat(LlmProvider.OPENROUTER.defaultKeyFormat()).isNull();
        assertThat(LlmProvider.ROUTER_9R.defaultKeyFormat()).isNull();

        assertThat(LlmProvider.OPENROUTER.acceptsKeyFormat(KeyFormat.OPENAI_FORMAT)).isTrue();
        assertThat(LlmProvider.OPENROUTER.acceptsKeyFormat(KeyFormat.ANTHROPIC_FORMAT)).isTrue();
        assertThat(LlmProvider.ROUTER_9R.acceptsKeyFormat(KeyFormat.OPENAI_FORMAT)).isTrue();
        assertThat(LlmProvider.ROUTER_9R.acceptsKeyFormat(KeyFormat.ANTHROPIC_FORMAT)).isTrue();

        assertThat(LlmProvider.OPENROUTER.acceptsKeyFormat(KeyFormat.GOOGLE_FORMAT)).isFalse();
        assertThat(LlmProvider.ROUTER_9R.acceptsKeyFormat(KeyFormat.GOOGLE_FORMAT)).isFalse();
    }
}
