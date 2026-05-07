package com.zeromail.core.llm.byok;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ZeroMailLlmByokPropertiesBindingTest {

    private final ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ZeroMailLlmByokPropertiesBindingConfiguration.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class));

    @Test
    void binds_byok_endpoint_policy_and_timeouts() {
        applicationContextRunner
                .withPropertyValues(
                        "zero-mail.llm.byok.allow-non-vendor-endpoints=true",
                        "zero-mail.llm.byok.allowed-extra-hosts[0]=llm.example.test",
                        "zero-mail.llm.byok.connect-timeout=7s",
                        "zero-mail.llm.byok.read-timeout=21s")
                .run(applicationContext -> {
                    ZeroMailLlmByokProperties byokProperties =
                            applicationContext.getBean(ZeroMailLlmByokProperties.class);

                    assertThat(byokProperties.allowNonVendorEndpoints()).isTrue();
                    assertThat(byokProperties.allowedExtraHosts()).containsExactly("llm.example.test");
                    assertThat(byokProperties.connectTimeout()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(byokProperties.readTimeout()).isEqualTo(Duration.ofSeconds(21));
                });
    }

    @Test
    void supplies_safe_defaults_when_optional_values_are_absent() {
        applicationContextRunner
                .withUserConfiguration(ZeroMailLlmByokPropertiesBindingConfiguration.class)
                .run(applicationContext -> {
                    ZeroMailLlmByokProperties byokProperties =
                            applicationContext.getBean(ZeroMailLlmByokProperties.class);

                    assertThat(byokProperties.allowNonVendorEndpoints()).isFalse();
                    assertThat(byokProperties.allowedExtraHosts()).isEmpty();
                    assertThat(byokProperties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(byokProperties.readTimeout()).isEqualTo(Duration.ofSeconds(15));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZeroMailLlmByokProperties.class)
    static class ZeroMailLlmByokPropertiesBindingConfiguration {
    }
}
