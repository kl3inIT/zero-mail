package com.zeromail.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiPropertiesTest {

    @Test
    void cors_defaults_include_web_and_admin_dev_origins() {
        ApiProperties properties = new ApiProperties(null, null, null);

        assertThat(properties.cors().allowedOrigins())
                .containsExactly("http://localhost:3000", "http://localhost:5174");
    }
}
