package com.zeromail.api.controllers.byok;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class ByokSaveProviderAllowListTest extends ByokControllerApiTestSupport {

    @Test
    void saveRejectsRouterProvidersWithProviderNotAllowedCode() throws Exception {
        Seed seed = seedUser("byok-provider-allow-list");

        ResponseEntity<String> response =
                postResponse(
                        authenticatedClient(seed),
                        "/api/byok",
                        Map.of(
                                "provider",
                                "OPENROUTER",
                                "baseUrl",
                                "https://openrouter.ai/api/v1",
                                "apiKey",
                                "sk-openrouter-1234567890"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("code").asString()).isEqualTo("ai.byok.provider_not_allowed");
    }
}
