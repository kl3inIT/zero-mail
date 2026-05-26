package com.zeromail.api.controllers.byok;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class ByokActivateGateModelMissingTest extends ByokControllerApiTestSupport {

    @Test
    void activateRejectsWhenModelIsMissing() throws Exception {
        Seed seed = seedUser("byok-activate-missing-model");
        saveByok(
                authenticatedClient(seed),
                "OPENAI",
                "https://api.openai.com/v1",
                "sk-model-missing-1234567890");
        jdbcTemplate.update(
                "UPDATE user_byok_key SET last_test_result = 'OK' WHERE tenant_id = ?",
                seed.tenantId());

        ResponseEntity<String> response =
                putResponse(authenticatedClient(seed), "/api/byok/active", Map.of("active", true));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("code").asString()).isEqualTo("ai.byok.no_model_picked");
    }
}
