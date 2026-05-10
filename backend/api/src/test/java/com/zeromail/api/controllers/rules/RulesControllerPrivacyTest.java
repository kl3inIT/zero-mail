package com.zeromail.api.controllers.rules;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.zeromail.api.security.TestSessionSupport;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.account.persistence.UserEntity;
import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.rules.exception.GmailPreviewUnavailableException;
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
@ExtendWith(OutputCaptureExtension.class)
class RulesControllerPrivacyTest extends ApiPostgresTestBase {

  private static final String RAW_HEADER_SENTINEL = "RAW_HEADER_SENTINEL_SHOULD_NOT_SURVIVE";
  private static final String RAW_BODY_SENTINEL = "RAW_BODY_SENTINEL_SHOULD_NOT_SURVIVE";
  private static final String RAW_SNIPPET_SENTINEL = "RAW_SNIPPET_SENTINEL_SHOULD_NOT_SURVIVE";
  private static final String PROMPT_SENTINEL = "PROMPT_SENTINEL_SHOULD_NOT_SURVIVE";
  private static final String COMPLETION_SENTINEL = "COMPLETION_SENTINEL_SHOULD_NOT_SURVIVE";

  @LocalServerPort int serverPort;

  @Autowired TenantRepository tenantRepository;

  @Autowired UserRepository userRepository;

  @Autowired TestSessionSupport.TestSessionMinter testSessionMinter;

  @Autowired ObjectMapper objectMapper;

  @MockitoBean RulePreviewDataService rulePreviewDataService;

  @Test
  void preview_response_and_logs_exclude_raw_content_prompt_and_completion_sentinels(
      CapturedOutput capturedOutput) throws Exception {
    SeedData seedData = seedUser("rules-privacy");
    JsonNode ruleJson = createRule(seedData, "Archive Stripe");
    UUID ruleId = UUID.fromString(ruleJson.path("ruleId").asString());
    when(rulePreviewDataService.fetchPreviewInputs(
            eq(seedData.tenantId()), eq(false), eq(new PreviewSampleSize(25))))
        .thenReturn(List.of(previewInputWithInternalSentinels()));

    JsonNode previewJson =
        postJson(
            authenticatedClient(seedData),
            "/api/rules/" + ruleId + "/preview",
            Map.of("sampleSize", 25));

    assertThat(previewJson.path("impactSummary").path("noWriteNotice").asBoolean()).isTrue();
    assertThat(previewJson.toString())
        .doesNotContain(
            RAW_HEADER_SENTINEL,
            RAW_BODY_SENTINEL,
            RAW_SNIPPET_SENTINEL,
            PROMPT_SENTINEL,
            COMPLETION_SENTINEL)
        .doesNotContain("rawHeaders", "rawBody", "snippet", "prompt", "completion");
    assertThat(capturedOutput.getOut() + capturedOutput.getErr())
        .doesNotContain(
            RAW_HEADER_SENTINEL,
            RAW_BODY_SENTINEL,
            RAW_SNIPPET_SENTINEL,
            PROMPT_SENTINEL,
            COMPLETION_SENTINEL);
  }

  @Test
  void gmail_preview_unavailable_maps_to_safe_rules_error_code() throws Exception {
    SeedData seedData = seedUser("rules-gmail-unavailable");
    JsonNode ruleJson = createRule(seedData, "Archive Stripe");
    UUID ruleId = UUID.fromString(ruleJson.path("ruleId").asString());
    when(rulePreviewDataService.fetchPreviewInputs(
            eq(seedData.tenantId()), eq(false), eq(new PreviewSampleSize(25))))
        .thenThrow(
            new GmailPreviewUnavailableException(
                GmailPreviewUnavailableException.Reason.NO_READ_GRANT));

    JsonNode problemJson =
        postProblem(
            authenticatedClient(seedData),
            "/api/rules/" + ruleId + "/preview",
            Map.of("sampleSize", 25));

    assertThat(problemJson.path("status").asInt()).isEqualTo(503);
    assertThat(problemJson.path("code").asString()).isEqualTo("error.rules.gmail.unavailable");
    assertThat(problemJson.path("params").path("reason").asString()).isEqualTo("no_read_grant");
    assertThat(problemJson.toString()).doesNotContain("token", "account", "gmail@example.test");
  }

  private JsonNode createRule(SeedData seedData, String displayName) throws Exception {
    return postJson(
        authenticatedClient(seedData),
        "/api/rules",
        Map.of(
            "displayName",
            displayName,
            "sourceText",
            "Archive Stripe receipts",
            "compiled",
            Map.of(
                "status",
                "compiled",
                "sourceLanguage",
                "en",
                "schemaVersion",
                "rules.v1",
                "matcherAst",
                "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}",
                "actionIntents",
                "[{\"type\":\"archive\"}]")));
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

  private static RulePreviewDataService.PreviewInput previewInputWithInternalSentinels() {
    return new RulePreviewDataService.PreviewInput(
        "gmail-privacy-1",
        "thread-privacy-1",
        new RulePreviewDataService.SafeMessageSummary(
            "billing@stripe.com",
            "stripe.com",
            "Safe subject excerpt",
            Instant.parse("2026-05-09T10:00:00Z"),
            List.of("INBOX")),
        new RuleEvaluationInput(
            "billing@stripe.com",
            "stripe.com",
            List.of("founder@example.test"),
            List.of(),
            "Safe subject excerpt",
            List.of("INBOX"),
            List.of(),
            Instant.parse("2026-05-09T10:00:00Z"),
            Instant.parse("2026-05-09T10:01:00Z"),
            false,
            false,
            false,
            Optional.of(true),
            Set.of(
                RAW_HEADER_SENTINEL,
                RAW_BODY_SENTINEL,
                RAW_SNIPPET_SENTINEL,
                PROMPT_SENTINEL,
                COMPLETION_SENTINEL)));
  }

  private record SeedData(UUID tenantId, String googleSubject, String email) {}
}
