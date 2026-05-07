package com.zeromail.core.llm.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class BYOKProviderJsonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void serializes_to_lowercase_id() throws Exception {
        String serializedProvider = objectMapper.writeValueAsString(BYOKProvider.OPENAI_COMPATIBLE);

        assertThat(serializedProvider).isEqualTo("\"openai-compatible\"");
    }

    @Test
    void deserializes_from_lowercase_id() throws Exception {
        BYOKProvider provider = objectMapper.readValue("\"anthropic\"", BYOKProvider.class);

        assertThat(provider).isEqualTo(BYOKProvider.ANTHROPIC);
    }

    @Test
    void round_trips_in_request_dto() throws Exception {
        BYOKProviderRequest request = objectMapper.readValue(
                "{\"provider\":\"openai-compatible\",\"endpoint\":\"https://x\",\"apiKey\":\"k\"}",
                BYOKProviderRequest.class);

        assertThat(request.provider()).isEqualTo(BYOKProvider.OPENAI_COMPATIBLE);
    }

    record BYOKProviderRequest(BYOKProvider provider, String endpoint, String apiKey) {}
}
