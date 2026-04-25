package com.zeromail.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeromail.api.support.ApiPostgresTestBase;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class OpenApiSchemaTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void spec_publishes_phase_1_operations() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);
        String body = client.get().uri("/v3/api-docs").retrieve().body(String.class);
        JsonNode root = new ObjectMapper().readTree(body);
        assertThat(root.path("info").path("version").asText()).isEqualTo("0.1.0");
        JsonNode paths = root.path("paths");
        assertThat(paths.has("/me")).isTrue();
        assertThat(paths.has("/tenant/status")).isTrue();
        assertThat(paths.has("/tenant/connect-gmail")).isTrue();
        assertThat(paths.has("/tenant/disconnect")).isTrue();
        assertThat(paths.has("/me/account")).isTrue();
        assertThat(paths.has("/onboarding/select-template")).isTrue();
        assertThat(paths.has("/onboarding/complete")).isTrue();
    }
}
