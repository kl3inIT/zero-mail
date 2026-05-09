package com.zeromail.api.controllers.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class RulesControllerTenantIsolationTest extends ApiPostgresTestBase {

  private static final String MATCHER_JSON =
      "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}";
  private static final String ACTIONS_JSON = "[{\"type\":\"archive\"}]";

  @LocalServerPort int serverPort;

  @Autowired TenantRepository tenantRepository;

  @Autowired UserRepository userRepository;

  @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

  @Autowired ObjectMapper objectMapper;

  @Test
  void tenant_a_cannot_read_mutate_preview_or_reorder_tenant_b_rules() throws Exception {
    SeedData tenantA = seedUser("rules-tenant-a");
    SeedData tenantB = seedUser("rules-tenant-b");
    JsonNode tenantARule = createRule(tenantA, "Tenant A rule");
    JsonNode tenantBRule = createRule(tenantB, "Tenant B rule");
    UUID tenantBRuleId = UUID.fromString(tenantBRule.path("ruleId").asString());

    JsonNode readProblem = getProblem(authenticatedClient(tenantA), "/api/rules/" + tenantBRuleId);
    assertThat(readProblem.path("code").asString()).isEqualTo("error.rules.not_found");

    JsonNode updateProblem =
        putProblem(
            authenticatedClient(tenantA),
            "/api/rules/" + tenantBRuleId,
            ruleSaveBody("Hijacked", "Hijacked source"));
    assertThat(updateProblem.path("code").asString()).isEqualTo("error.rules.not_found");

    JsonNode deleteProblem = deleteProblem(authenticatedClient(tenantA), tenantBRuleId);
    assertThat(deleteProblem.path("code").asString()).isEqualTo("error.rules.not_found");

    JsonNode reorderProblem =
        putProblem(
            authenticatedClient(tenantA),
            "/api/rules/reorder",
            Map.of(
                "entries",
                List.of(
                    Map.of(
                        "ruleId",
                        tenantBRuleId.toString(),
                        "entityVersion",
                        tenantBRule.path("entityVersion").asInt()))));
    assertThat(reorderProblem.path("code").asString()).isEqualTo("error.rules.reorder.invalid");

    JsonNode tenantAList = getJson(authenticatedClient(tenantA), "/api/rules");
    assertThat(tenantAList.path("rules").toString())
        .contains(tenantARule.path("ruleId").asString());
    assertThat(tenantAList.path("rules").toString()).doesNotContain(tenantBRuleId.toString());
  }

  @Test
  void reorder_version_mismatch_rejects_entire_request_without_partial_order_changes()
      throws Exception {
    SeedData seedData = seedUser("rules-reorder-conflict");
    JsonNode firstRule = createRule(seedData, "First rule");
    JsonNode secondRule = createRule(seedData, "Second rule");

    JsonNode reorderProblem =
        putProblem(
            authenticatedClient(seedData),
            "/api/rules/reorder",
            Map.of(
                "entries",
                List.of(
                    Map.of(
                        "ruleId",
                        secondRule.path("ruleId").asString(),
                        "entityVersion",
                        secondRule.path("entityVersion").asInt() + 1),
                    Map.of(
                        "ruleId",
                        firstRule.path("ruleId").asString(),
                        "entityVersion",
                        firstRule.path("entityVersion").asInt()))));
    assertThat(reorderProblem.path("code").asString()).isEqualTo("error.rules.version_mismatch");

    JsonNode rulesAfterFailure = getJson(authenticatedClient(seedData), "/api/rules");
    assertThat(rulesAfterFailure.path("rules").get(0).path("ruleId").asString())
        .isEqualTo(firstRule.path("ruleId").asString());
    assertThat(rulesAfterFailure.path("rules").get(1).path("ruleId").asString())
        .isEqualTo(secondRule.path("ruleId").asString());
  }

  private JsonNode createRule(SeedData seedData, String displayName) throws Exception {
    ResponseEntity<String> response =
        authenticatedClient(seedData)
            .post()
            .uri("/api/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(ruleSaveBody(displayName, "Archive Stripe")))
            .retrieve()
            .toEntity(String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private Map<String, Object> ruleSaveBody(String displayName, String sourceText) {
    return Map.of(
        "displayName",
        displayName,
        "sourceText",
        sourceText,
        "compiled",
        Map.of(
            "status",
            "compiled",
            "sourceLanguage",
            "en",
            "schemaVersion",
            "rules.v1",
            "matcherAst",
            MATCHER_JSON,
            "actionIntents",
            ACTIONS_JSON));
  }

  private JsonNode getJson(RestClient restClient, String uri) throws Exception {
    ResponseEntity<String> response = restClient.get().uri(uri).retrieve().toEntity(String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private JsonNode getProblem(RestClient restClient, String uri) throws Exception {
    ResponseEntity<String> response =
        restClient
            .get()
            .uri(uri)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (_, _) -> {})
            .toEntity(String.class);
    assertThat(response.getStatusCode().isError()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private JsonNode putProblem(RestClient restClient, String uri, Object body) throws Exception {
    ResponseEntity<String> response =
        restClient
            .put()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve()
            .onStatus(HttpStatusCode::isError, (_, _) -> {})
            .toEntity(String.class);
    assertThat(response.getStatusCode().isError()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private JsonNode deleteProblem(RestClient restClient, UUID ruleId) throws Exception {
    ResponseEntity<String> response =
        restClient
            .delete()
            .uri("/api/rules/{ruleId}", ruleId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (_, _) -> {})
            .toEntity(String.class);
    assertThat(response.getStatusCode().isError()).isTrue();
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
                    new UserEntity(UUID.randomUUID(), tenantId, googleSubject, email)));
    testSessionMinter.mint(googleSubject, email);
    return new SeedData(tenantId, googleSubject, email);
  }

  private record SeedData(UUID tenantId, String googleSubject, String email) {}
}
