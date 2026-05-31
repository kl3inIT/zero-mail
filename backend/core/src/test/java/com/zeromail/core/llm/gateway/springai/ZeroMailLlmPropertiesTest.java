package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.config.BillingProperties;
import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.llm.config.LlmProperties.PlatformProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ZeroMailLlmPropertiesTest {

    private final ApplicationContextRunner applicationContextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ZeroMailLlmPropertiesBindingConfiguration.class)
                    .withConfiguration(
                            org.springframework.boot.autoconfigure.AutoConfigurations.of(
                                    ConfigurationPropertiesAutoConfiguration.class))
                    .withPropertyValues(
                            "zero-mail.crypto.refresh-token-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Test
    void binds_platform_properties_and_model_pins() {
        applicationContextRunner
                .withPropertyValues(
                        "zero-mail.llm.platform.provider=openai",
                        "zero-mail.llm.platform.base-url=https://openrouter.ai/api/v1",
                        "zero-mail.llm.platform.api-key=test-platform-key",
                        "zero-mail.llm.platform.compile-model=openai/gpt-5.4-nano",
                        "zero-mail.llm.platform.drift-model=openai/gpt-5.4-nano",
                        "zero-mail.llm.platform.triage-model=anthropic/claude-3.5-sonnet",
                        "zero-mail.llm.platform.draft-model=anthropic/claude-3-haiku")
                .run(
                        applicationContext -> {
                            PlatformProperties llmProperties =
                                    applicationContext.getBean(LlmProperties.class).platform();

                            assertThat(llmProperties.provider()).isEqualTo("openai");
                            assertThat(llmProperties.baseUrl())
                                    .isEqualTo("https://openrouter.ai/api/v1");
                            assertThat(llmProperties.apiKey()).isEqualTo("test-platform-key");
                            assertThat(llmProperties.compileModel())
                                    .isEqualTo("openai/gpt-5.4-nano");
                            assertThat(llmProperties.driftModel()).isEqualTo("openai/gpt-5.4-nano");
                            assertThat(llmProperties.triageModel())
                                    .isEqualTo("anthropic/claude-3.5-sonnet");
                            assertThat(llmProperties.draftModel())
                                    .isEqualTo("anthropic/claude-3-haiku");
                            assertThat(
                                            applicationContext
                                                    .getBean(BillingProperties.class)
                                                    .cost()
                                                    .triageDeterministic())
                                    .isZero();
                        });
    }

    /**
     * No-value-drift bind smoke (quick task w9t Task 6e). Supplies ONLY the deployment-required
     * api-key — exactly the yml shape after Task 2 deleted the 6 previously-literal platform values
     * (provider, base-url, 4 model pins, 2 timeouts). Asserts every deleted literal is reproduced
     * byte-identically by the {@link PlatformProperties} compact-constructor defaults, so the
     * structure-only refactor changed no runtime-effective value.
     */
    @Test
    void platform_defaults_match_deleted_yml_literals_when_only_api_key_supplied() {
        applicationContextRunner
                .withPropertyValues("zero-mail.llm.platform.api-key=dummy")
                .run(
                        applicationContext -> {
                            PlatformProperties llmProperties =
                                    applicationContext.getBean(LlmProperties.class).platform();

                            assertThat(llmProperties.provider()).isEqualTo("openai");
                            assertThat(llmProperties.baseUrl())
                                    .isEqualTo("https://openrouter.ai/api/v1");
                            assertThat(llmProperties.compileModel())
                                    .isEqualTo("openai/gpt-5.4-nano");
                            assertThat(llmProperties.driftModel()).isEqualTo("openai/gpt-5.4-nano");
                            assertThat(llmProperties.triageModel())
                                    .isEqualTo("openai/gpt-5.4-nano");
                            assertThat(llmProperties.draftModel()).isEqualTo("openai/gpt-5.4-nano");
                            assertThat(llmProperties.connectTimeout())
                                    .isEqualTo(Duration.ofSeconds(5));
                            assertThat(llmProperties.readTimeout())
                                    .isEqualTo(Duration.ofSeconds(30));
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({LlmProperties.class, BillingProperties.class})
    static class ZeroMailLlmPropertiesBindingConfiguration {}
}
