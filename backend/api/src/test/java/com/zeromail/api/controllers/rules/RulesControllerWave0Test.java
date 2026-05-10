package com.zeromail.api.controllers.rules;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class RulesControllerWave0Test extends ApiPostgresTestBase {

  private static final String PLAN_03_07_CONTROLLER_MESSAGE =
      "Plan 03-07 lands RulesController endpoints, DTOs, and error mapping";

  @LocalServerPort int serverPort;

  @Autowired TenantRepository tenantRepository;

  @Autowired UserRepository userRepository;

  @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

  @Autowired ObjectMapper objectMapper;

  @Test
  @Disabled(PLAN_03_07_CONTROLLER_MESSAGE)
  void list_rules_materializes_templates_and_remains_tenant_scoped() throws Exception {
    SeedData tenantOne = seedUser("rules-list-tenant-one");
    SeedData tenantTwo = seedUser("rules-list-tenant-two");

    ResponseEntity<String> tenantOneResponse =
        authenticatedClient(tenantOne)
            .get()
            .uri("/api/rules")
            .retrieve()
            .toEntity(String.class);
    ResponseEntity<String> tenantTwoResponse =
        authenticatedClient(tenantTwo)
            .get()
            .uri("/api/rules")
            .retrieve()
            .toEntity(String.class);

    assertThat(tenantOneResponse.getStatusCode().value()).isEqualTo(200);
    assertThat(tenantTwoResponse.getStatusCode().value()).isEqualTo(200);
    assertThat(tenantOneResponse.getBody()).doesNotContain(tenantTwo.tenantId().toString());
  }

  @Test
  @Disabled(PLAN_03_07_CONTROLLER_MESSAGE)
  void controller_contract_declares_all_planned_rules_endpoints() throws Exception {
    JsonNode openApi = apiDocs();

    assertThat(openApi.path("paths").has("/api/rules")).isTrue();
    assertThat(openApi.path("paths").has("/api/rules/compile")).isTrue();
    assertThat(openApi.path("paths").has("/api/rules/reorder")).isTrue();
    assertThat(openApi.path("paths").has("/api/rules/{ruleId}")).isTrue();
    assertThat(openApi.path("paths").has("/api/rules/{ruleId}/preview")).isTrue();
    assertThat(openApi.path("paths").has("/api/rules/{ruleId}/enable")).isTrue();
    assertThat(openApi.path("paths").has("/api/rules/{ruleId}/disable")).isTrue();
    assertThat(openApi.path("paths").has("/api/rules/templates/{templateKey}/enable")).isTrue();
  }

  @Test
  @Disabled(PLAN_03_07_CONTROLLER_MESSAGE)
  void invalid_sample_size_returns_stable_rules_error_code() throws Exception {
    SeedData seedData = seedUser("rules-invalid-sample-size");

    ResponseEntity<String> response =
        authenticatedClient(seedData)
            .post()
            .uri("/api/rules/{ruleId}/preview", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"sampleSize\":51}")
            .retrieve()
            .onStatus(HttpStatusCode::isError, (_, _) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(objectMapper.readTree(response.getBody()).path("code").asString())
        .isEqualTo("error.rules.preview.invalid_sample_size");
  }

  @Test
  @Disabled(PLAN_03_07_CONTROLLER_MESSAGE)
  void insufficient_credits_for_compile_or_preview_maps_to_existing_billing_code()
      throws Exception {
    SeedData seedData = seedUser("rules-insufficient-credits");

    ResponseEntity<String> response =
        authenticatedClient(seedData)
            .post()
            .uri("/api/rules/compile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"sourceText\":\"Archive Stripe receipts\"}")
            .retrieve()
            .onStatus(HttpStatusCode::isError, (_, _) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(402);
    assertThat(objectMapper.readTree(response.getBody()).path("code").asString())
        .isEqualTo("error.billing.insufficient");
  }

  @Test
  @Disabled(PLAN_03_07_CONTROLLER_MESSAGE)
  void ambiguous_compile_returns_one_clarification_and_no_rule_id() throws Exception {
    SeedData seedData = seedUser("rules-ambiguous-compile");

    ResponseEntity<String> response =
        authenticatedClient(seedData)
            .post()
            .uri("/api/rules/compile")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"sourceText\":\"Clean up newsletters\"}")
            .retrieve()
            .toEntity(String.class);
    JsonNode responseJson = objectMapper.readTree(response.getBody());

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(responseJson.path("clarification").isMissingNode()).isFalse();
    assertThat(responseJson.path("clarification").path("question").asString()).isNotBlank();
    assertThat(responseJson.path("ruleId").isMissingNode()).isTrue();
  }

  @Test
  @Disabled(PLAN_03_07_CONTROLLER_MESSAGE)
  void preview_response_excludes_raw_content_prompts_and_completions() throws Exception {
    SeedData seedData = seedUser("rules-preview-privacy");

    ResponseEntity<String> response =
        authenticatedClient(seedData)
            .post()
            .uri("/api/rules/{ruleId}/preview", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"sampleSize\":25}")
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody())
        .contains("No Gmail changes were made.")
        .doesNotContain("rawBody", "rawHeaders", "snippet", "prompt", "completion");
  }

  private JsonNode apiDocs() {
    ResponseEntity<String> response = client().get().uri("/v3/api-docs").retrieve().toEntity(String.class);
    return objectMapper.readTree(response.getBody());
  }

  private RestClient authenticatedClient(SeedData seedData) {
    return RestClient.builder()
        .baseUrl("http://localhost:" + serverPort)
        .defaultHeader(TestSessionSupport.HEADER_SUBJECT, seedData.googleSubject())
        .defaultHeader(TestSessionSupport.HEADER_EMAIL, seedData.email())
        .build();
  }

  private RestClient client() {
    return RestClient.create("http://localhost:" + serverPort);
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
                    new UserEntity(UUID.randomUUID(), tenantId, googleSubject, email)));
    testSessionMinter.mint(googleSubject, email);
    return new SeedData(tenantId, googleSubject, email);
  }

  private record SeedData(UUID tenantId, String googleSubject, String email) {}
}
