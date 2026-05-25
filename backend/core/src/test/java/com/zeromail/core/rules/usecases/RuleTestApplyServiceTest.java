package com.zeromail.core.rules.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuleTestApplyServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000777");
    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Test
    void appliesOnlyExplicitLabelActionsFromEnabledRuleTest() throws Exception {
        RulePreviewService rulePreviewService = mock(RulePreviewService.class);
        TriageGmailWriter triageGmailWriter = mock(TriageGmailWriter.class);
        TriageAuditWriter triageAuditWriter = mock(TriageAuditWriter.class);
        RuleRepository ruleRepository = mock(RuleRepository.class);
        RulePreviewResult previewResult = previewResult();
        when(rulePreviewService.previewAllEnabled(TENANT_ID, 100, false)).thenReturn(previewResult);
        when(triageGmailWriter.applyLabel(TENANT_ID, "gmail-1", "Finance"))
                .thenReturn("Label_finance");
        when(ruleRepository.findOrderedByTenantId(TENANT_ID)).thenReturn(List.of());

        RuleTestApplyService ruleTestApplyService =
                new RuleTestApplyService(
                        rulePreviewService, triageGmailWriter, triageAuditWriter, ruleRepository);

        RuleTestApplyService.RuleTestApplyResult applyResult =
                ruleTestApplyService.applyLabelsForEnabledRules(TENANT_ID, 100, false);

        assertThat(applyResult.previewResult()).isSameAs(previewResult);
        assertThat(applyResult.appliedLabelCount()).isEqualTo(1);
        assertThat(applyResult.affectedMessageCount()).isEqualTo(1);
        assertThat(applyResult.appliedLabels().getFirst().labelName()).isEqualTo("Finance");
        assertThat(applyResult.appliedLabels().getFirst().gmailLabelId())
                .isEqualTo("Label_finance");
        verify(triageGmailWriter).applyLabel(TENANT_ID, "gmail-1", "Finance");
        verify(triageAuditWriter)
                .recordRuleTestAppliedLabel(
                        TENANT_ID,
                        "gmail-1",
                        "thread-1",
                        "Receipt from Stripe",
                        "billing@stripe.com",
                        RULE_ID,
                        "Rule " + RULE_ID,
                        "Finance",
                        "Label_finance");
    }

    private static RulePreviewResult previewResult() {
        return new RulePreviewResult(
                new RulePreviewResult.ImpactSummary(
                        100,
                        1,
                        1,
                        Map.of("label", 1, "archive", 1),
                        0,
                        0,
                        true,
                        "rules.preview.noGmailChanges"),
                List.of(
                        new RulePreviewResult.PreviewRow(
                                "gmail-1",
                                "thread-1",
                                "billing@stripe.com",
                                "stripe.com",
                                "Receipt from Stripe",
                                Instant.parse("2026-05-23T08:00:00Z"),
                                List.of("INBOX"),
                                true,
                                List.of(
                                        new RulePreviewResult.ActionChip(
                                                "label",
                                                "label:Finance",
                                                List.of(RULE_ID),
                                                List.of("sender")),
                                        new RulePreviewResult.ActionChip(
                                                "archive",
                                                "archive",
                                                List.of(UUID.randomUUID()),
                                                List.of("subject"))),
                                List.of(),
                                List.of(),
                                List.of())),
                false);
    }
}
