package com.zeromail.core.rules.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zeromail.core.rules.domain.ActionIntent;
import com.zeromail.core.rules.domain.MatcherNode;
import com.zeromail.core.rules.domain.PreviewSampleSize;
import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
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

class RulePreviewServiceTest extends PostgresContainerTest {

    @Autowired RuleManagementService ruleManagementService;

    @Autowired RulePreviewService rulePreviewService;

    @Autowired RuleRepository ruleRepository;

    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean RulePreviewDataService rulePreviewDataService;

    @Test
    void saved_rule_preview_includes_current_disabled_rule_and_enabled_siblings_only()
            throws Exception {
        UUID tenantId = seedTenant("rules-preview-saved");
        var currentRule =
                withTenant(
                        tenantId,
                        () ->
                                ruleManagementService.create(
                                        createCommand(
                                                tenantId,
                                                "Label Stripe",
                                                "Label Stripe receipts",
                                                "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}",
                                                "[{\"type\":\"label\",\"labelName\":\"Finance\"}]")));
        var enabledSibling =
                withTenant(
                        tenantId,
                        () ->
                                ruleManagementService.create(
                                        createCommand(
                                                tenantId,
                                                "Archive receipts",
                                                "Archive receipts",
                                                "{\"schemaVersion\":\"rules.v1\",\"type\":\"SUBJECT_CONTAINS\",\"text\":\"receipt\"}",
                                                "[{\"type\":\"archive\"}]")));
        var unrelatedDisabled =
                withTenant(
                        tenantId,
                        () ->
                                ruleManagementService.create(
                                        createCommand(
                                                tenantId,
                                                "Ignored disabled",
                                                "Ignored disabled",
                                                "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}",
                                                "[{\"type\":\"label\",\"labelName\":\"Ignored\"}]")));
        withTenant(
                tenantId,
                () ->
                        ruleManagementService.markPreviewSucceeded(
                                tenantId,
                                enabledSibling.ruleId().value(),
                                enabledSibling.entityVersion(),
                                Instant.parse("2026-05-10T00:00:00Z")));
        withTenant(
                tenantId,
                () -> ruleManagementService.enable(tenantId, enabledSibling.ruleId().value()));
        when(rulePreviewDataService.fetchPreviewInputs(
                        eq(tenantId), eq(false), eq(new PreviewSampleSize(100))))
                .thenReturn(List.of(previewInput()));

        RulePreviewResult previewResult =
                withTenant(
                        tenantId,
                        () ->
                                rulePreviewService.previewSavedRule(
                                        tenantId, currentRule.ruleId().value(), null));

        assertThat(previewResult.savedRuleMarkedPreviewed()).isTrue();
        assertThat(previewResult.impactSummary().sampleSize()).isEqualTo(100);
        assertThat(previewResult.impactSummary().sampledMessageCount()).isEqualTo(1);
        assertThat(previewResult.impactSummary().matchedCount()).isEqualTo(1);
        assertThat(previewResult.impactSummary().noWriteNotice()).isTrue();
        assertThat(previewResult.impactSummary().noWriteNoticeKey())
                .isEqualTo("rules.preview.noGmailChanges");
        assertThat(previewResult.impactSummary().proposedActionCounts())
                .containsEntry("label", 1)
                .containsEntry("archive", 1)
                .doesNotContainEntry("label", 2);
        assertThat(previewResult.rows().getFirst().proposedActionChips())
                .extracting(RulePreviewResult.ActionChip::safeLabel)
                .containsExactly("label:Finance", "archive");

        RuleEntity previewedCurrentRule =
                withTenant(
                        tenantId,
                        () ->
                                ruleRepository
                                        .findByIdAndTenantId(currentRule.ruleId().value(), tenantId)
                                        .orElseThrow());
        RuleEntity ignoredRule =
                withTenant(
                        tenantId,
                        () ->
                                ruleRepository
                                        .findByIdAndTenantId(
                                                unrelatedDisabled.ruleId().value(), tenantId)
                                        .orElseThrow());
        assertThat(previewedCurrentRule.getLastPreviewedEntityVersion())
                .isEqualTo(currentRule.entityVersion());
        assertThat(previewedCurrentRule.isEnabled()).isFalse();
        assertThat(ignoredRule.getLastPreviewedEntityVersion()).isNull();
    }

    @Test
    void draft_preview_uses_validated_payload_and_rejects_invalid_sample_sizes() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        when(rulePreviewDataService.fetchPreviewInputs(
                        eq(tenantId), eq(false), eq(new PreviewSampleSize(10))))
                .thenReturn(List.of(previewInput()));

        RulePreviewResult previewResult =
                rulePreviewService.preview(
                        RulePreviewCommand.draft(
                                tenantId,
                                new MatcherNode.SenderDomainMatcher("sender-domain", "stripe.com"),
                                List.of(new ActionIntent.Archive()),
                                10));

        assertThat(previewResult.savedRuleMarkedPreviewed()).isFalse();
        assertThat(previewResult.impactSummary().sampleSize()).isEqualTo(10);
        assertThat(previewResult.rows().getFirst().proposedActionChips())
                .extracting(RulePreviewResult.ActionChip::actionTypeId)
                .containsExactly("archive");
        assertThatThrownBy(
                        () ->
                                rulePreviewService.preview(
                                        RulePreviewCommand.draft(
                                                tenantId,
                                                new MatcherNode.SenderDomainMatcher(
                                                        "sender-domain", "stripe.com"),
                                                List.of(new ActionIntent.Archive()),
                                                51)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private RuleCreateCommand createCommand(
            UUID tenantId,
            String displayName,
            String sourceText,
            String matcherAst,
            String actionIntents) {
        return new RuleCreateCommand(
                UUID.randomUUID(),
                tenantId,
                displayName,
                sourceText,
                RuleCompileResult.compiled(
                        RuleLanguage.EN,
                        displayName,
                        RuleSchemaVersion.RULES_V1,
                        matcherAst,
                        actionIntents),
                null,
                null);
    }

    private static RulePreviewDataService.PreviewInput previewInput() {
        return new RulePreviewDataService.PreviewInput(
                "gmail-1",
                "thread-1",
                new RulePreviewDataService.SafeMessageSummary(
                        "billing@stripe.com",
                        "stripe.com",
                        "Receipt from Stripe",
                        Instant.parse("2026-05-09T10:00:00Z"),
                        List.of("INBOX")),
                new com.zeromail.core.rules.domain.RuleEvaluationInput(
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

    private <T> T withTenant(UUID tenantId, Supplier<T> supplier) throws Exception {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(supplier::get);
    }
}
