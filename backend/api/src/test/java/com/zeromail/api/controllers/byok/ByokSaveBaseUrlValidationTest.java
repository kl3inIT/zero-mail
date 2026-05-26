package com.zeromail.api.controllers.byok;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class ByokSaveBaseUrlValidationTest extends ByokControllerApiTestSupport {

    @Test
    void saveRejectsNonHttpsBaseUrl() throws Exception {
        Seed seed = seedUser("byok-base-url-http");

        ResponseEntity<String> response =
                postResponse(
                        authenticatedClient(seed),
                        "/api/byok",
                        Map.of(
                                "provider",
                                "OPENAI",
                                "baseUrl",
                                "http://attacker.com",
                                "apiKey",
                                "sk-http-1234567890"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("code").asString()).isEqualTo("ai.byok.base_url_not_https");
    }

    @Test
    void saveAcceptsPublicHttpsBaseUrl() {
        Seed seed = seedUser("byok-base-url-https");

        ResponseEntity<String> response =
                postResponse(
                        authenticatedClient(seed),
                        "/api/byok",
                        Map.of(
                                "provider",
                                "OPENAI",
                                "baseUrl",
                                "https://api.openai.com/v1",
                                "apiKey",
                                "sk-https-1234567890"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }
}
