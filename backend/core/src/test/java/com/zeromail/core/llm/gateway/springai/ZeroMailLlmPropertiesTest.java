package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.llm.model.BYOKProvider;

class ZeroMailLlmPropertiesTest {

    private final ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ZeroMailLlmPropertiesBindingConfiguration.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class));

    @Test
    void binds_platform_properties_and_model_pins() {
        applicationContextRunner
                .withPropertyValues(
                        "zero-mail.llm.platform.provider=openai-compatible",
                        "zero-mail.llm.platform.base-url=https://openrouter.ai/api/v1",
                        "zero-mail.llm.platform.api-key=test-platform-key",
                        "zero-mail.llm.platform.compile-model=openai/gpt-4o-mini",
                        "zero-mail.llm.platform.drift-model=openai/gpt-4o-mini",
                        "zero-mail.llm.platform.triage-model=anthropic/claude-3.5-sonnet")
                .run(applicationContext -> {
                    ZeroMailLlmProperties llmProperties = applicationContext.getBean(ZeroMailLlmProperties.class);

                    assertThat(llmProperties.provider()).isEqualTo(BYOKProvider.OPENAI_COMPATIBLE);
                    assertThat(llmProperties.baseUrl()).isEqualTo("https://openrouter.ai/api/v1");
                    assertThat(llmProperties.apiKey()).isEqualTo("test-platform-key");
                    assertThat(llmProperties.compileModel()).isEqualTo("openai/gpt-4o-mini");
                    assertThat(llmProperties.driftModel()).isEqualTo("openai/gpt-4o-mini");
                    assertThat(llmProperties.triageModel()).isEqualTo("anthropic/claude-3.5-sonnet");
                    assertThat(llmProperties.modelByCallSite())
                            .containsEntry(CallSite.PREVIEW, "openai/gpt-4o-mini")
                            .containsEntry(CallSite.DRAFT, "openai/gpt-4o-mini")
                            .containsEntry(CallSite.TRIAGE, "anthropic/claude-3.5-sonnet");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZeroMailLlmProperties.class)
    static class ZeroMailLlmPropertiesBindingConfiguration {
    }
}
