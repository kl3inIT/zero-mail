package com.zeromail.api.controllers.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import com.zeromail.core.llm.byok.BaseUrlValidator;
import com.zeromail.core.llm.gateway.springai.ConnectionTestResult;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class ByokTestConnectionEnumOnlyTest extends ByokControllerApiTestSupport {

    @ParameterizedTest(name = "{0} returns OK with models")
    @MethodSource("providers")
    void providerReturnsOkWithModels(LlmProvider provider, String baseUrl) throws Exception {
        Seed seed = seedUser("byok-test-ok-" + provider.id().toLowerCase());
        saveByok(authenticatedClient(seed), provider.id(), baseUrl, "sk-ok-1234567890");
        when(providerConnectionTester.probeConnection(
                        eq(provider),
                        any(BaseUrlValidator.ValidatedTarget.class),
                        any(byte[].class)))
                .thenReturn(
                        new ConnectionTestResult(
                                MasterKeyTestResult.OK,
                                List.of(
                                        provider.id().toLowerCase() + "-model-a",
                                        "shared-model-b")));

        ResponseEntity<String> response =
                postResponse(authenticatedClient(seed), "/api/byok/test-connection", Map.of());
        JsonNode json = objectMapper.readTree(response.getBody());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.path("result").asString()).isEqualTo("OK");
        assertThat(json.path("models").isArray()).isTrue();
        assertThat(json.path("models").size()).isEqualTo(2);
        assertThat(json.has("data")).isFalse();
        assertThat(json.has("display_name")).isFalse();
        assertThat(json.has("capabilities")).isFalse();
        assertThat(json.has("max_input_tokens")).isFalse();
    }

    @ParameterizedTest(name = "{0} returns INVALID_KEY without leak")
    @MethodSource("providers")
    void providerReturnsInvalidKeyWithoutLeak(LlmProvider provider, String baseUrl)
            throws Exception {
        Seed seed = seedUser("byok-test-invalid-" + provider.id().toLowerCase());
        saveByok(authenticatedClient(seed), provider.id(), baseUrl, "sk-invalid-1234567890");
        when(providerConnectionTester.probeConnection(
                        eq(provider),
                        any(BaseUrlValidator.ValidatedTarget.class),
                        any(byte[].class)))
                .thenReturn(new ConnectionTestResult(MasterKeyTestResult.INVALID_KEY, List.of()));

        ResponseEntity<String> response =
                postResponse(authenticatedClient(seed), "/api/byok/test-connection", Map.of());
        JsonNode json = objectMapper.readTree(response.getBody());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.path("result").asString()).isEqualTo("INVALID_KEY");
        assertThat(json.has("models")).isFalse();
        assertThat(response.getBody()).doesNotContain("sk-leaked-AB12CD34", "SENTINEL_LEAK_X9Z2");
    }

    @ParameterizedTest(name = "{0} caps models at 100")
    @MethodSource("providers")
    void providerModelListIsCappedAt100(LlmProvider provider, String baseUrl) throws Exception {
        Seed seed = seedUser("byok-test-cap-" + provider.id().toLowerCase());
        saveByok(authenticatedClient(seed), provider.id(), baseUrl, "sk-cap-1234567890");
        List<String> modelIds =
                IntStream.range(0, 150)
                        .mapToObj(index -> provider.id().toLowerCase() + "-model-" + index)
                        .toList();
        when(providerConnectionTester.probeConnection(
                        eq(provider),
                        any(BaseUrlValidator.ValidatedTarget.class),
                        any(byte[].class)))
                .thenReturn(new ConnectionTestResult(MasterKeyTestResult.OK, modelIds));

        ResponseEntity<String> response =
                postResponse(authenticatedClient(seed), "/api/byok/test-connection", Map.of());
        JsonNode json = objectMapper.readTree(response.getBody());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.path("result").asString()).isEqualTo("OK");
        assertThat(json.path("models").size()).isEqualTo(100);
    }

    static Stream<Arguments> providers() {
        return Stream.of(
                Arguments.of(LlmProvider.OPENAI, "https://api.openai.com/v1"),
                Arguments.of(LlmProvider.ANTHROPIC, "https://api.anthropic.com/v1"),
                Arguments.of(
                        LlmProvider.GOOGLE, "https://generativelanguage.googleapis.com/v1beta"),
                Arguments.of(LlmProvider.DEEPSEEK, "https://api.deepseek.com"));
    }
}
