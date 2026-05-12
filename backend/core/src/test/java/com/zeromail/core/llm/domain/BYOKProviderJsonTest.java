package com.zeromail.core.llm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class BYOKProviderJsonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void serializes_to_lowercase_id() throws Exception {
        String serializedProvider = objectMapper.writeValueAsString(BYOKProvider.OPENAI);

        assertThat(serializedProvider).isEqualTo("\"openai\"");
    }

    @Test
    void deserializes_from_lowercase_id() throws Exception {
        BYOKProvider provider = objectMapper.readValue("\"anthropic\"", BYOKProvider.class);

        assertThat(provider).isEqualTo(BYOKProvider.ANTHROPIC);
    }

    @Test
    void round_trips_in_request_dto() throws Exception {
        BYOKProviderRequest request =
                objectMapper.readValue(
                        "{\"provider\":\"openai\",\"endpoint\":\"https://x\",\"apiKey\":\"k\"}",
                        BYOKProviderRequest.class);

        assertThat(request.provider()).isEqualTo(BYOKProvider.OPENAI);
    }

    @Test
    void legacy_openai_compatible_id_deserializes_to_openai() throws Exception {
        BYOKProvider provider = objectMapper.readValue("\"openai-compatible\"", BYOKProvider.class);

        assertThat(provider).isEqualTo(BYOKProvider.OPENAI);
    }

    @Test
    void preset_round_trips_from_lowercase_id() throws Exception {
        BYOKPresetRequest request =
                objectMapper.readValue(
                        "{\"preset\":\"anthropic-compatible\",\"endpoint\":\"https://x\",\"apiKey\":\"k\"}",
                        BYOKPresetRequest.class);

        assertThat(request.preset()).isEqualTo(ByokProviderPreset.ANTHROPIC_COMPATIBLE);
    }

    record BYOKProviderRequest(BYOKProvider provider, String endpoint, String apiKey) {}

    record BYOKPresetRequest(ByokProviderPreset preset, String endpoint, String apiKey) {}
}
