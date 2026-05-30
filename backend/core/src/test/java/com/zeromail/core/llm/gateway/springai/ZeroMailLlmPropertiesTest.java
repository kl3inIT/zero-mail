package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.config.BillingProperties;
import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.llm.config.LlmProperties.PlatformProperties;
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({LlmProperties.class, BillingProperties.class})
    static class ZeroMailLlmPropertiesBindingConfiguration {}
}
