package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class PlatformApiKeyTest {

    @Test
    void getValue_reads_properties_at_call_time() {
        AtomicReference<String> platformApiKey = new AtomicReference<>("key-A");
        PlatformApiKey apiKey = new PlatformApiKey(platformApiKey::get);

        assertThat(apiKey.getValue()).isEqualTo("key-A");

        platformApiKey.set("key-B");

        assertThat(apiKey.getValue()).isEqualTo("key-B");
    }
}
