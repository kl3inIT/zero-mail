package com.zeromail.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZeroMailApiPropertiesTest {

    @Test
    void cors_defaults_include_web_and_admin_dev_origins() {
        ZeroMailApiProperties properties = new ZeroMailApiProperties(null, null, null);

        assertThat(properties.cors().allowedOrigins())
                .containsExactly("http://localhost:3000", "http://localhost:5174");
    }
}
