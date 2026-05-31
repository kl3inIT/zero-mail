package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.llm.config.LlmProperties;
import com.zeromail.core.llm.config.LlmProperties.ByokProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ZeroMailLlmByokPropertiesBindingTest {

    private final ApplicationContextRunner applicationContextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ZeroMailLlmByokPropertiesBindingConfiguration.class)
                    .withConfiguration(
                            org.springframework.boot.autoconfigure.AutoConfigurations.of(
                                    ConfigurationPropertiesAutoConfiguration.class))
                    .withPropertyValues(
                            "zero-mail.crypto.refresh-token-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                            "zero-mail.llm.platform.api-key=test-platform-key");

    @Test
    void binds_byok_endpoint_policy_and_timeouts() {
        applicationContextRunner
                .withPropertyValues(
                        "zero-mail.llm.byok.allow-non-vendor-endpoints=true",
                        "zero-mail.llm.byok.allowed-extra-hosts[0]=llm.example.test",
                        "zero-mail.llm.byok.allowed-extra-ports[0]=8443",
                        "zero-mail.llm.byok.connect-timeout=7s",
                        "zero-mail.llm.byok.read-timeout=21s")
                .run(
                        applicationContext -> {
                            ByokProperties byokProperties =
                                    applicationContext.getBean(LlmProperties.class).byok();

                            assertThat(byokProperties.allowNonVendorEndpoints()).isTrue();
                            assertThat(byokProperties.allowedExtraHosts())
                                    .containsExactly("llm.example.test");
                            assertThat(byokProperties.allowedExtraPorts()).containsExactly(8443);
                            assertThat(byokProperties.connectTimeout())
                                    .isEqualTo(Duration.ofSeconds(7));
                            assertThat(byokProperties.readTimeout())
                                    .isEqualTo(Duration.ofSeconds(21));
                        });
    }

    @Test
    void supplies_safe_defaults_when_optional_values_are_absent() {
        applicationContextRunner
                .withUserConfiguration(ZeroMailLlmByokPropertiesBindingConfiguration.class)
                .run(
                        applicationContext -> {
                            ByokProperties byokProperties =
                                    applicationContext.getBean(LlmProperties.class).byok();

                            assertThat(byokProperties.allowNonVendorEndpoints()).isFalse();
                            assertThat(byokProperties.allowedExtraHosts()).isEmpty();
                            assertThat(byokProperties.allowedExtraPorts()).isEmpty();
                            assertThat(byokProperties.connectTimeout())
                                    .isEqualTo(Duration.ofSeconds(5));
                            assertThat(byokProperties.readTimeout())
                                    .isEqualTo(Duration.ofSeconds(15));
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LlmProperties.class)
    static class ZeroMailLlmByokPropertiesBindingConfiguration {}
}
