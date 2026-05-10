package com.zeromail.core.rules.privacy;


import com.zeromail.core.rules.domain.PreviewSampleSize;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.zeromail.core.rules.application.RuleCompileResult;
import com.zeromail.core.rules.application.RuleCreateCommand;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.application.RulePreviewResult;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.service.RuleManagementService;
import com.zeromail.core.rules.service.RulePreviewDataService;
import com.zeromail.core.rules.service.RulePreviewService;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

@SuppressWarnings("SqlResolve")
class RulePreviewPrivacyTest extends PostgresContainerTest {

  private static final String RAW_HEADER_SENTINEL = "RAW_HEADER_SENTINEL_SHOULD_NOT_SURVIVE";
  private static final String RAW_BODY_SENTINEL = "RAW_BODY_SENTINEL_SHOULD_NOT_SURVIVE";
  private static final String PROMPT_SENTINEL = "PROMPT_SENTINEL_SHOULD_NOT_SURVIVE";
  private static final String COMPLETION_SENTINEL = "COMPLETION_SENTINEL_SHOULD_NOT_SURVIVE";

  @Autowired RuleManagementService ruleManagementService;

  @Autowired RulePreviewService rulePreviewService;

  @Autowired JdbcTemplate jdbcTemplate;

  @MockitoBean RulePreviewDataService rulePreviewDataService;

  @Test
  void preview_result_and_durable_rule_rows_exclude_raw_content_prompt_and_completion_sentinels()
      throws Exception {
    UUID tenantId = seedTenant("rules-preview-privacy");
    var rule =
        withTenant(
            tenantId,
            () ->
                ruleManagementService.create(
                    new RuleCreateCommand(
                        UUID.randomUUID(),
                        tenantId,
                        "Archive Stripe",
                        "Archive Stripe receipts",
                        RuleCompileResult.compiled(
                            RuleLanguage.EN,
                            "Archive Stripe",
                            RuleSchemaVersion.RULES_V1,
                            "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}",
                            "[{\"type\":\"archive\"}]"),
                        null,
                        null)));
    when(rulePreviewDataService.fetchPreviewInputs(
            eq(tenantId), eq(false), eq(new com.zeromail.core.rules.domain.PreviewSampleSize(25))))
        .thenReturn(List.of(previewInput()));

    RulePreviewResult previewResult =
        withTenant(
            tenantId,
            () -> rulePreviewService.previewSavedRule(tenantId, rule.ruleId().value(), null));

    String serializedResult = String.valueOf(previewResult);
    assertThat(serializedResult)
        .doesNotContain(
            RAW_HEADER_SENTINEL, RAW_BODY_SENTINEL, PROMPT_SENTINEL, COMPLETION_SENTINEL)
        .doesNotContain("rawHeaders", "rawBody", "snippet", "prompt", "completion");

    String durableRuleState =
        jdbcTemplate.queryForObject(
            """
            select coalesce(source_text, '') || coalesce(matcher_ast::text, '') || coalesce(action_intents::text, '')
            from rules
            where tenant_id = ? and id = ?
            """,
            String.class,
            tenantId,
            rule.ruleId().value());
    assertThat(durableRuleState)
        .doesNotContain(
            RAW_HEADER_SENTINEL, RAW_BODY_SENTINEL, PROMPT_SENTINEL, COMPLETION_SENTINEL);
  }

  private UUID seedTenant(String displayName) {
    UUID tenantId = UUID.randomUUID();
    jdbcTemplate.update(
        "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
    return tenantId;
  }

  private static RulePreviewDataService.PreviewInput previewInput() {
    return new RulePreviewDataService.PreviewInput(
        "gmail-privacy-1",
        "thread-privacy-1",
        new RulePreviewDataService.SafeMessageSummary(
            "billing@stripe.com",
            "stripe.com",
            "Safe subject excerpt",
            Instant.parse("2026-05-09T10:00:00Z"),
            List.of("INBOX")),
        new com.zeromail.core.rules.domain.RuleEvaluationInput(
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
            Optional.empty(),
            Set.of()));
  }

  private <T> T withTenant(UUID tenantId, Supplier<T> supplier) throws Exception {
    return ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(supplier::get);
  }
}
