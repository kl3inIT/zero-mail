package com.zeromail.api.controllers.byok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class ByokSaveSsrfRejectionTest extends ByokControllerApiTestSupport {

    @Test
    void saveRejectsLinkLocalPrivateIpv6AndReboundLoopbackHosts() throws Exception {
        assertRejectedHost("https://169.254.169.254", "ai.byok.base_url_host_private");
        assertRejectedHost("https://10.0.0.5", "ai.byok.base_url_host_private");
        assertRejectedHost("https://[::1]", "ai.byok.base_url_host_private");

        when(hostResolver.resolve(eq("localhost.evil.tld"))).thenReturn(addresses("127.0.0.1"));
        assertRejectedHost("https://localhost.evil.tld", "ai.byok.base_url_host_private");
    }

    @Test
    void saveAcceptsPublicProviderHost() {
        Seed seed = seedUser("byok-ssrf-positive");

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
                                "sk-public-1234567890"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    private void assertRejectedHost(String baseUrl, String expectedCode) throws Exception {
        Seed seed = seedUser("byok-ssrf-negative");

        ResponseEntity<String> response =
                postResponse(
                        authenticatedClient(seed),
                        "/api/byok",
                        Map.of(
                                "provider",
                                "OPENAI",
                                "baseUrl",
                                baseUrl,
                                "apiKey",
                                "sk-private-1234567890"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("code").asString()).isEqualTo(expectedCode);
    }
}
