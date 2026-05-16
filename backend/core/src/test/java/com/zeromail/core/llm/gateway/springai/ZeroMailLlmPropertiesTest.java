package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.config.ZeroMailCoreProperties;
import com.zeromail.core.config.ZeroMailCoreProperties.ZeroMailLlmProperties;
import com.zeromail.core.llm.domain.BYOKProvider;
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
                            "zero-mail.crypto.refresh-token-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                            "zero-mail.billing.sepay.webhook-api-key=test-sepay-key-fixture",
                            "zero-mail.billing.payment-account.bank-code=VCB",
                            "zero-mail.billing.payment-account.bank-name=Vietcombank",
                            "zero-mail.billing.payment-account.account-number=0123456789",
                            "zero-mail.billing.payment-account.account-name=Zero Mail Test");

    @Test
    void binds_platform_properties_and_model_pins() {
        applicationContextRunner
                .withPropertyValues(
                        "zero-mail.llm.platform.provider=openai",
                        "zero-mail.llm.platform.base-url=https://openrouter.ai/api/v1",
                        "zero-mail.llm.platform.api-key=test-platform-key",
                        "zero-mail.llm.platform.compile-model=openai/gpt-5.4-nano",
                        "zero-mail.llm.platform.drift-model=openai/gpt-5.4-nano",
                        "zero-mail.llm.platform.triage-model=anthropic/claude-3.5-sonnet")
                .run(
                        applicationContext -> {
                            ZeroMailLlmProperties llmProperties =
                                    applicationContext
                                            .getBean(ZeroMailCoreProperties.class)
                                            .llm()
                                            .platform();

                            assertThat(llmProperties.provider()).isEqualTo(BYOKProvider.OPENAI);
                            assertThat(llmProperties.baseUrl())
                                    .isEqualTo("https://openrouter.ai/api/v1");
                            assertThat(llmProperties.apiKey()).isEqualTo("test-platform-key");
                            assertThat(llmProperties.compileModel())
                                    .isEqualTo("openai/gpt-5.4-nano");
                            assertThat(llmProperties.driftModel()).isEqualTo("openai/gpt-5.4-nano");
                            assertThat(llmProperties.triageModel())
                                    .isEqualTo("anthropic/claude-3.5-sonnet");
                            assertThat(llmProperties.modelByCallSite())
                                    .containsEntry(CallSite.PREVIEW, "openai/gpt-5.4-nano")
                                    .containsEntry(CallSite.DRAFT, "anthropic/claude-3.5-sonnet")
                                    .containsEntry(CallSite.TRIAGE, "anthropic/claude-3.5-sonnet")
                                    .containsEntry(
                                            CallSite.TRIAGE_PLATFORM_LLM,
                                            "anthropic/claude-3.5-sonnet")
                                    .containsEntry(
                                            CallSite.TRIAGE_DETERMINISTIC,
                                            "anthropic/claude-3.5-sonnet");
                            assertThat(
                                            applicationContext
                                                    .getBean(ZeroMailCoreProperties.class)
                                                    .billing()
                                                    .cost()
                                                    .triageDeterministic())
                                    .isZero();
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZeroMailCoreProperties.class)
    static class ZeroMailLlmPropertiesBindingConfiguration {}
}
