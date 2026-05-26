package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderAllowListTest {

    private final ProviderAllowList providerAllowList = new ProviderAllowList();

    @ParameterizedTest
    @CsvSource({"OPENAI", "ANTHROPIC", "GOOGLE", "DEEPSEEK"})
    void accepts_user_byok_supported_providers(String providerId) {
        assertThat(providerAllowList.validateForByok(providerId))
                .isEqualTo(LlmProvider.fromId(providerId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPENROUTER", "ROUTER_9R"})
    void rejects_routing_providers_for_user_byok(String providerId) {
        assertThatThrownBy(() -> providerAllowList.validateForByok(providerId))
                .isInstanceOf(ProviderAllowList.ProviderNotAllowedException.class)
                .satisfies(
                        throwable ->
                                assertThat(
                                                ((ProviderAllowList.ProviderNotAllowedException)
                                                                throwable)
                                                        .errorCode())
                                        .isEqualTo("ai.byok.provider_not_allowed"));
    }
}
