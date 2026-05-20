package com.zeromail.core.llm.gateway.springai.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
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
        ModelsProbeClient modelsProbeClient =
                new ModelsProbeClient(restClientBuilder, new ObjectMapper());

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
    void sends_google_key_in_google_header() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models"))
                .andExpect(header("x-goog-api-key", "google-key"))
                .andRespond(
                        withSuccess(
                                "{\"models\":[]}",
                                org.springframework.http.MediaType.APPLICATION_JSON));
        ModelsProbeClient modelsProbeClient =
                new ModelsProbeClient(restClientBuilder, new ObjectMapper());

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
