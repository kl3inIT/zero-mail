package com.zeromail.api.controllers.rules;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.exception.InsufficientCreditsException;
import com.zeromail.core.llm.application.RuleCompileGatewayResult;
import com.zeromail.core.llm.service.LlmGateway;
import com.zeromail.core.rules.domain.PreviewSampleSize;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import com.zeromail.core.rules.service.RulePreviewDataService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class RulesControllerIntegrationTest extends ApiPostgresTestBase {

  private static final String STRIPE_MATCHER_JSON =
      "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}";
  private static final String ARCHIVE_ACTIONS_JSON = "[{\"type\":\"archive\"}]";

  @LocalServerPort int serverPort;

  @Autowired TenantRepository tenantRepository;

  @Autowired UserRepository userRepository;

  @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

  @Autowired ObjectMapper objectMapper;

  @Autowired JdbcTemplate jdbcTemplate;

  @MockitoBean LlmGateway llmGateway;

  @MockitoBean RulePreviewDataService rulePreviewDataService;

  @Test
  void rules_api_supports_compile_crud_preview_enable_reorder_delete_and_templates()
      throws Exception {
    SeedData seedData = seedUser("rules-api-happy-path");
    when(llmGateway.compileRule(eq(CallSite.PREVIEW), anyString()))
        .thenReturn(compiledGatewayResult("Archive Stripe receipts", STRIPE_MATCHER_JSON));

    JsonNode compileJson =
        postJson(
            authenticatedClient(seedData),
            "/api/rules/compile",
            Map.of("sourceText", "Archive receipts from Stripe"));
    assertThat(compileJson.path("status").asString()).isEqualTo("compiled");
    assertThat(compileJson.path("compiled").path("matcherAst").asString())
        .contains("SENDER_DOMAIN", "stripe.com");

    JsonNode firstRuleJson =
        createRule(
            seedData,
            "Archive Stripe receipts",
            "Archive receipts from Stripe",
            compileJson.path("compiled"));
    UUID firstRuleId = UUID.fromString(firstRuleJson.path("ruleId").asString());
    assertThat(firstRuleJson.path("enabled").asBoolean()).isFalse();
    assertThat(firstRuleJson.path("entityVersion").asInt()).isZero();

    JsonNode fetchedRuleJson = getJson(authenticatedClient(seedData), "/api/rules/" + firstRuleId);
    assertThat(fetchedRuleJson.path("entityVersion").asInt()).isZero();
    assertThat(fetchedRuleJson.path("lastPreviewedEntityVersion").isNull()).isTrue();

    JsonNode secondRuleJson =
        createRule(
            seedData,
            "Archive Stripe invoices",
            "Archive invoices from Stripe",
            compileJson.path("compiled"));
    UUID secondRuleId = UUID.fromString(secondRuleJson.path("ruleId").asString());
    when(rulePreviewDataService.fetchPreviewInputs(
            eq(seedData.tenantId()), eq(false), eq(new PreviewSampleSize(25))))
        .thenReturn(List.of(previewInput()));

    JsonNode previewJson =
        postJson(
            authenticatedClient(seedData),
            "/api/rules/" + firstRuleId + "/preview",
            Map.of("sampleSize", 25));
    assertThat(previewJson.path("impactSummary").path("sampleSize").asInt()).isEqualTo(25);
    assertThat(previewJson.path("savedRuleMarkedPreviewed").asBoolean()).isTrue();
    assertThat(previewJson.toString())
        .doesNotContain("rawBody", "rawHeaders", "prompt", "completion");

    JsonNode enabledRuleJson =
        patchJson(
            authenticatedClient(seedData),
            "/api/rules/" + firstRuleId + "/enabled",
            Map.of("enabled", true));
    assertThat(enabledRuleJson.path("enabled").asBoolean()).isTrue();

    JsonNode disabledRuleJson =
        patchJson(
            authenticatedClient(seedData),
            "/api/rules/" + firstRuleId + "/enabled",
            Map.of("enabled", false));
    assertThat(disabledRuleJson.path("enabled").asBoolean()).isFalse();

    JsonNode currentRuleBeforeUpdate =
        getJson(authenticatedClient(seedData), "/api/rules/" + firstRuleId);
    JsonNode updatedRuleJson =
        putJson(
            authenticatedClient(seedData),
            "/api/rules/" + firstRuleId,
            ruleSaveBody(
                "Archive Stripe mail",
                "Archive all Stripe mail",
                compileJson.path("compiled"),
                currentRuleBeforeUpdate.path("entityVersion").asInt()));
    assertThat(updatedRuleJson.path("displayName").asString()).isEqualTo("Archive Stripe mail");
    assertThat(updatedRuleJson.path("lastPreviewedEntityVersion").isNull()).isTrue();

    JsonNode currentRulesJson = getJson(authenticatedClient(seedData), "/api/rules");
    assertThat(currentRulesJson.path("rules").size()).isGreaterThanOrEqualTo(2);
    JsonNode reorderedRulesJson =
        putJson(
            authenticatedClient(seedData),
            "/api/rules/reorder",
            Map.of(
                "entries",
                List.of(
                    Map.of(
                        "ruleId",
                        secondRuleId.toString(),
                        "entityVersion",
                        secondRuleJson.path("entityVersion").asInt()),
                    Map.of(
                        "ruleId",
                        firstRuleId.toString(),
                        "entityVersion",
                        updatedRuleJson.path("entityVersion").asInt()))));
    assertThat(reorderedRulesJson.get(0).path("ruleId").asString())
        .isEqualTo(secondRuleId.toString());

    JsonNode templateCatalogJson = getJson(authenticatedClient(seedData), "/api/rules/templates");
    assertThat(templateCatalogJson.size()).isGreaterThanOrEqualTo(3);
    JsonNode materializedTemplateJson =
        postJson(
            authenticatedClient(seedData),
            "/api/rules/templates/archive-receipts/materialize",
            Map.of());
    assertThat(materializedTemplateJson.path("createdCount").asInt()).isLessThanOrEqualTo(1);
    assertThat(materializedTemplateJson.path("skippedTemplates").toString())
        .doesNotContain("matcher", "action", "prompt", "completion");

    ResponseEntity<String> deleteResponse =
        authenticatedClient(seedData)
            .delete()
            .uri("/api/rules/{ruleId}", secondRuleId)
            .retrieve()
            .toEntity(String.class);
    assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);
  }

  @Test
  void list_rules_materializes_selected_templates_and_uses_no_store_cache() throws Exception {
    SeedData seedData = seedUser("rules-api-materialize-list");
    insertSelection(seedData.tenantId(), "archive-receipts", true);
    insertSelection(seedData.tenantId(), "label-newsletters", true);

    ResponseEntity<String> firstResponse =
        authenticatedClient(seedData).get().uri("/api/rules").retrieve().toEntity(String.class);
    ResponseEntity<String> secondResponse =
        authenticatedClient(seedData).get().uri("/api/rules").retrieve().toEntity(String.class);

    assertThat(firstResponse.getHeaders().getCacheControl()).isEqualTo("no-store");
    JsonNode firstResponseJson = objectMapper.readTree(firstResponse.getBody());
    JsonNode secondResponseJson = objectMapper.readTree(secondResponse.getBody());
    assertThat(firstResponseJson.path("materialization").path("createdCount").asInt()).isEqualTo(2);
    assertThat(firstResponseJson.path("rules").size()).isEqualTo(2);
    assertThat(secondResponseJson.path("materialization").path("createdCount").asInt()).isZero();
    assertThat(secondResponseJson.path("materialization").path("skippedCount").asInt()).isEqualTo(2);
    assertThat(secondResponseJson.path("rules").size()).isEqualTo(2);
    assertThat(ruleCount(seedData.tenantId())).isEqualTo(2);
  }

  @Test
  void materialize_selected_templates_is_explicit_post_and_idempotent() throws Exception {
    SeedData seedData = seedUser("rules-api-materialize-selected");
    insertSelection(seedData.tenantId(), "archive-receipts", true);
    insertSelection(seedData.tenantId(), "label-newsletters", true);

    JsonNode firstMaterializationJson =
        postJson(
            authenticatedClient(seedData), "/api/rules/templates/materialize-selected", Map.of());
    JsonNode secondMaterializationJson =
        postJson(
            authenticatedClient(seedData), "/api/rules/templates/materialize-selected", Map.of());

    assertThat(firstMaterializationJson.path("createdCount").asInt()).isEqualTo(2);
    assertThat(secondMaterializationJson.path("createdCount").asInt()).isZero();
    assertThat(secondMaterializationJson.path("skippedCount").asInt()).isEqualTo(2);
    assertThat(ruleCount(seedData.tenantId())).isEqualTo(2);
  }

  @Test
  void compile_clarification_invalid_output_and_credit_errors_have_distinct_contracts()
      throws Exception {
    SeedData seedData = seedUser("rules-api-compile-states");
    when(llmGateway.compileRule(eq(CallSite.PREVIEW), anyString()))
        .thenReturn(
            new RuleCompileGatewayResult(
                "rule_compile",
                "test-model",
                Map.of(
                    "sourceLanguage",
                    "en",
                    "clarificationRequired",
                    true,
                    "clarificationQuestion",
                    "Should Zero Mail archive newsletters or only label them?")));

    JsonNode clarificationJson =
        postJson(
            authenticatedClient(seedData),
            "/api/rules/compile",
            Map.of("sourceText", "Clean up newsletters"));
    assertThat(clarificationJson.path("status").asString()).isEqualTo("clarificationRequired");
    assertThat(clarificationJson.path("clarification").path("question").asString()).isNotBlank();
    assertThat(clarificationJson.path("compiled").isNull()).isTrue();

    JsonNode clarificationCreateProblem =
        postProblem(
            authenticatedClient(seedData),
            "/api/rules",
            ruleSaveBody(
                "Needs clarification",
                "Clean up newsletters",
                clarificationPayload("Should Zero Mail archive newsletters or only label them?")));
    assertThat(clarificationCreateProblem.path("code").asString())
        .isEqualTo("error.rules.compile.clarification_required");

    when(llmGateway.compileRule(eq(CallSite.PREVIEW), anyString()))
        .thenThrow(new InsufficientCreditsException());
    JsonNode insufficientCreditsProblem =
        postProblem(
            authenticatedClient(seedData),
            "/api/rules/compile",
            Map.of("sourceText", "Archive receipts from Stripe"));
    assertThat(insufficientCreditsProblem.path("status").asInt()).isEqualTo(402);
    assertThat(insufficientCreditsProblem.path("code").asString())
        .isEqualTo("error.billing.insufficient");
  }

  @Test
  void validation_errors_invalid_sample_size_and_unsafe_actions_return_stable_codes()
      throws Exception {
    SeedData seedData = seedUser("rules-api-validation");
    JsonNode invalidSampleSizeProblem =
        postProblem(
            authenticatedClient(seedData),
            "/api/rules/" + UUID.randomUUID() + "/preview",
            Map.of("sampleSize", 51));
    assertThat(invalidSampleSizeProblem.path("code").asString())
        .isEqualTo("error.rules.preview.invalid_sample_size");

    JsonNode unsafeActionProblem =
        postProblem(
            authenticatedClient(seedData),
            "/api/rules",
            ruleSaveBody(
                "Unsafe",
                "Send this email",
                compiledPayload(STRIPE_MATCHER_JSON, "[{\"type\":\"send\"}]")));
    assertThat(unsafeActionProblem.path("code").asString()).isEqualTo("error.rules.unsafe_action");
  }

  @Test
  void tampered_compiled_rule_payloads_are_rejected_before_persistence() throws Exception {
    SeedData seedData = seedUser("rules-api-tampered-compiled");

    JsonNode missingMatcherFieldProblem =
        postProblem(
            authenticatedClient(seedData),
            "/api/rules",
            ruleSaveBody(
                "Tampered matcher",
                "Archive Stripe",
                compiledPayload(
                    "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\"}",
                    ARCHIVE_ACTIONS_JSON)));
    JsonNode unknownMatcherFieldProblem =
        postProblem(
            authenticatedClient(seedData),
            "/api/rules",
            ruleSaveBody(
                "Prompt leak",
                "Archive Stripe",
                compiledPayload(
                    """
                    {
                      "schemaVersion":"rules.v1",
                      "type":"SENDER_DOMAIN",
                      "domain":"stripe.com",
                      "prompt":"hidden"
                    }
                    """,
                    ARCHIVE_ACTIONS_JSON)));
    JsonNode missingActionFieldProblem =
        postProblem(
            authenticatedClient(seedData),
            "/api/rules",
            ruleSaveBody(
                "Missing label",
                "Label Stripe",
                compiledPayload(STRIPE_MATCHER_JSON, "[{\"type\":\"label\"}]")));

    assertThat(missingMatcherFieldProblem.path("code").asString())
        .isEqualTo("error.rules.compile.invalid");
    assertThat(unknownMatcherFieldProblem.path("code").asString())
        .isEqualTo("error.rules.compile.invalid");
    assertThat(missingActionFieldProblem.path("code").asString())
        .isEqualTo("error.rules.compile.invalid");
    assertThat(ruleCount(seedData.tenantId())).isZero();
  }

  @Test
  void openapi_declares_rules_paths_and_reorder_entity_version_shape() throws Exception {
    JsonNode openApiJson =
        getJson(RestClient.create("http://localhost:" + serverPort), "/v3/api-docs");

    assertThat(openApiJson.path("paths").has("/api/rules")).isTrue();
    assertThat(openApiJson.path("paths").has("/api/rules/compile")).isTrue();
    assertThat(openApiJson.path("paths").has("/api/rules/reorder")).isTrue();
    assertThat(openApiJson.path("paths").has("/api/rules/{ruleId}")).isTrue();
    assertThat(openApiJson.path("paths").has("/api/rules/{ruleId}/preview")).isTrue();
    assertThat(openApiJson.path("paths").has("/api/rules/{ruleId}/enabled")).isTrue();
    assertThat(openApiJson.path("paths").has("/api/rules/templates")).isTrue();
    assertThat(openApiJson.path("paths").has("/api/rules/templates/{templateKey}/materialize"))
        .isTrue();
    assertThat(openApiJson.path("paths").has("/api/rules/templates/materialize-selected"))
        .isTrue();
    String openApiBody = openApiJson.toString();
    assertThat(openApiBody).contains("RuleOrderEntryRequest", "ruleId", "entityVersion");
  }

  private JsonNode createRule(
      SeedData seedData, String displayName, String sourceText, JsonNode compiledPayload)
      throws Exception {
    return postJson(
        authenticatedClient(seedData),
        "/api/rules",
        ruleSaveBody(displayName, sourceText, compiledPayload));
  }

  private Map<String, Object> ruleSaveBody(
      String displayName, String sourceText, Object compiledPayload) {
    return Map.of(
        "displayName", displayName, "sourceText", sourceText, "compiled", compiledPayload);
  }

  private Map<String, Object> ruleSaveBody(
      String displayName, String sourceText, Object compiledPayload, int entityVersion) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("displayName", displayName);
    body.put("sourceText", sourceText);
    body.put("compiled", compiledPayload);
    body.put("entityVersion", entityVersion);
    return body;
  }

  private Map<String, Object> compiledPayload(String matcherAst, String actionIntents) {
    Map<String, Object> compiledPayload = new LinkedHashMap<>();
    compiledPayload.put("status", "compiled");
    compiledPayload.put("sourceLanguage", "en");
    compiledPayload.put("schemaVersion", "rules.v1");
    compiledPayload.put("matcherAst", matcherAst);
    compiledPayload.put("actionIntents", actionIntents);
    return compiledPayload;
  }

  private Map<String, Object> clarificationPayload(String question) {
    return Map.of("status", "clarificationRequired", "sourceLanguage", "en", "question", question);
  }

  private RuleCompileGatewayResult compiledGatewayResult(String displayName, String matcherJson) {
    return new RuleCompileGatewayResult(
        "rule_compile",
        "test-model",
        Map.of(
            "schemaVersion",
            "rules.v1",
            "sourceLanguage",
            "en",
            "displayName",
            displayName,
            "matcher",
            Map.of("type", "SENDER_DOMAIN", "domain", "stripe.com"),
            "actionIntents",
            List.of(Map.of("type", "archive")),
            "clarificationRequired",
            false));
  }

  private JsonNode postJson(RestClient restClient, String uri, Object body) throws Exception {
    ResponseEntity<String> response =
        restClient
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve()
            .toEntity(String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private JsonNode patchJson(RestClient restClient, String uri, Object body) throws Exception {
    ResponseEntity<String> response =
        restClient
            .patch()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve()
            .toEntity(String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private JsonNode putJson(RestClient restClient, String uri, Object body) throws Exception {
    ResponseEntity<String> response =
        restClient
            .put()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve()
            .toEntity(String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private JsonNode postProblem(RestClient restClient, String uri, Object body) throws Exception {
    ResponseEntity<String> response =
        restClient
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve()
            .onStatus(HttpStatusCode::isError, (_, _) -> {})
            .toEntity(String.class);
    assertThat(response.getStatusCode().isError()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private JsonNode getJson(RestClient restClient, String uri) throws Exception {
    ResponseEntity<String> response = restClient.get().uri(uri).retrieve().toEntity(String.class);
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
                    new UserEntity(UUID.randomUUID(), tenantId, googleSubject, email)));
    testSessionMinter.mint(googleSubject, email);
    return new SeedData(tenantId, googleSubject, email);
  }

  private void insertSelection(UUID tenantId, String templateKey, boolean enabled) {
    jdbcTemplate.update(
        "insert into onboarding_selections(id, tenant_id, template_key, enabled) values (?, ?, ?, ?)",
        UUID.randomUUID(),
        tenantId,
        templateKey,
        enabled);
  }

  private int ruleCount(UUID tenantId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from rules where tenant_id = ?", Integer.class, tenantId);
    return count == null ? 0 : count;
  }

  private static RulePreviewDataService.PreviewInput previewInput() {
    return new RulePreviewDataService.PreviewInput(
        "gmail-rules-1",
        "thread-rules-1",
        new RulePreviewDataService.SafeMessageSummary(
            "billing@stripe.com",
            "stripe.com",
            "Receipt from Stripe",
            Instant.parse("2026-05-09T10:00:00Z"),
            List.of("INBOX")),
        new RuleEvaluationInput(
            "billing@stripe.com",
            "stripe.com",
            List.of("founder@example.test"),
            List.of(),
            "Receipt from Stripe",
            List.of("INBOX"),
            List.of(),
            Instant.parse("2026-05-09T10:00:00Z"),
            Instant.parse("2026-05-09T10:01:00Z"),
            false,
            false,
            false,
            Optional.empty(),
            Set.of()));
  }

  private record SeedData(UUID tenantId, String googleSubject, String email) {}
}
