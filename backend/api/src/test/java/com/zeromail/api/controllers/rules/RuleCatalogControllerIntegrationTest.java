package com.zeromail.api.controllers.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class RuleCatalogControllerIntegrationTest extends ApiPostgresTestBase {

    @LocalServerPort int serverPort;

    @Autowired TenantRepository tenantRepository;

    @Autowired UserRepository userRepository;

    @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

    @Autowired ObjectMapper objectMapper;

    @Test
    void user_catalog_endpoints_localize_examples_actions_and_publish_openapi_paths()
            throws Exception {
        SeedData seedData = seedUser("rule-catalog-user-api");
        RestClient restClient = authenticatedClient(seedData);

        JsonNode examplesJson = getJson(restClient, "/api/rules/catalog/examples?locale=vi");
        assertThat(examplesJson.path("personas").get(0).path("personaKey").asString())
                .isEqualTo("founder");
        assertThat(examplesJson.path("personas").get(0).path("displayName").asString())
                .isEqualTo("Nhà sáng lập");
        assertThat(
                        examplesJson
                                .path("personas")
                                .get(0)
                                .path("examples")
                                .get(0)
                                .path("exampleText")
                                .asString())
                .isNotBlank();

        JsonNode actionsJson = getJson(restClient, "/api/rules/catalog/actions?locale=vi");
        assertThat(actionsJson.path("actions").size()).isGreaterThanOrEqualTo(10);
        assertThat(actionsJson.path("actions").toString())
                .contains("send_reply", "Gửi trả lời", "HIGH", "AVAILABLE");

        JsonNode openApiJson =
                getJson(RestClient.create("http://localhost:" + serverPort), "/v3/api-docs");
        assertThat(openApiJson.path("paths").has("/api/rules/catalog/examples")).isTrue();
        assertThat(openApiJson.path("paths").has("/api/rules/catalog/actions")).isTrue();
    }

    @Test
    void automation_settings_endpoint_defaults_on_and_persists_toggle() throws Exception {
        SeedData seedData = seedUser("rule-automation-settings-api");
        RestClient restClient = authenticatedClient(seedData);

        JsonNode initialSettingsJson = getJson(restClient, "/api/rules/settings/automation");
        assertThat(initialSettingsJson.path("autoSendRulesEnabled").asBoolean()).isTrue();

        ResponseEntity<String> updateResponse =
                restClient
                        .put()
                        .uri("/api/rules/settings/automation")
                        .body(Map.of("autoSendRulesEnabled", false))
                        .retrieve()
                        .toEntity(String.class);
        assertThat(updateResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode updatedSettingsJson = objectMapper.readTree(updateResponse.getBody());
        assertThat(updatedSettingsJson.path("autoSendRulesEnabled").asBoolean()).isFalse();

        JsonNode openApiJson =
                getJson(RestClient.create("http://localhost:" + serverPort), "/v3/api-docs");
        assertThat(openApiJson.path("paths").has("/api/rules/settings/automation")).isTrue();
    }

    private JsonNode getJson(RestClient restClient, String uri) throws Exception {
        ResponseEntity<String> response =
                restClient.get().uri(uri).retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return objectMapper.readTree(response.getBody());
    }

    private RestClient authenticatedClient(SeedData seedData) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + serverPort)
                .defaultHeader(TestSessionSupport.HEADER_SUBJECT, seedData.googleSubject())
                .defaultHeader(TestSessionSupport.HEADER_EMAIL, seedData.email())
                .build();
    }

    private SeedData seedUser(String label) {
        UUID tenantId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, label));
        String googleSubject = "sub-" + label;
        String email = label + "@example.test";
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                userRepository.save(
                                        new UserEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleSubject,
                                                email)));
        testSessionMinter.mint(googleSubject, email);
        return new SeedData(tenantId, googleSubject, email);
    }

    private record SeedData(UUID tenantId, String googleSubject, String email) {}
}
