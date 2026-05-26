package com.zeromail.core.llm.gateway.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import com.zeromail.core.admin.mkey.usecases.MasterKeyTestResult;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ModelsProbeClientTest {

    @Test
    void maps_unauthorized_provider_response_to_invalid_key_without_body() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.openai.com/v1/models"))
                .andRespond(
                        withStatus(HttpStatus.UNAUTHORIZED)
                                .body("{\"error\":\"provider detail\"}"));
        // ModelsProbeClient now takes a second builder for cleartext (h2c-disabled) targets.
        // Both test cases hit HTTPS URLs, so reuse the same builder for both — the cleartext
        // path is not exercised here.
        ModelsProbeClient modelsProbeClient =
                new ModelsProbeClient(restClientBuilder, restClientBuilder, new ObjectMapper());

        assertThat(
                        modelsProbeClient.probe(
                                LlmProvider.OPENAI,
                                KeyFormat.OPENAI_FORMAT,
                                "https://api.openai.com/v1",
                                "invalid".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(MasterKeyTestResult.INVALID_KEY);

        server.verify();
    }

    @Test
    void sends_anthropic_key_and_version_headers_without_bearer_header() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.anthropic.com/v1/models"))
                .andExpect(header("x-api-key", "anthropic-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(
                        withSuccess(
                                "{\"data\":[{\"id\":\"claude-haiku-4-1\"}]}",
                                org.springframework.http.MediaType.APPLICATION_JSON));
        ModelsProbeClient modelsProbeClient =
                new ModelsProbeClient(restClientBuilder, restClientBuilder, new ObjectMapper());

        assertThat(
                        modelsProbeClient.probe(
                                LlmProvider.ANTHROPIC,
                                KeyFormat.ANTHROPIC_FORMAT,
                                "https://api.anthropic.com/v1",
                                "anthropic-key".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(MasterKeyTestResult.OK);

        server.verify();
    }

    @Test
    void sends_google_key_in_google_header() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models"))
                .andExpect(header("x-goog-api-key", "google-key"))
                .andRespond(
                        withSuccess(
                                "{\"models\":[]}",
                                org.springframework.http.MediaType.APPLICATION_JSON));
        // ModelsProbeClient now takes a second builder for cleartext (h2c-disabled) targets.
        // Both test cases hit HTTPS URLs, so reuse the same builder for both — the cleartext
        // path is not exercised here.
        ModelsProbeClient modelsProbeClient =
                new ModelsProbeClient(restClientBuilder, restClientBuilder, new ObjectMapper());

        assertThat(
                        modelsProbeClient.probe(
                                LlmProvider.GOOGLE,
                                KeyFormat.GOOGLE_FORMAT,
                                "https://generativelanguage.googleapis.com/v1beta",
                                "google-key".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(MasterKeyTestResult.OK);

        server.verify();
    }
}
