package com.zeromail.core.rules.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.zeromail.core.rules.model.ActionIntent;
import com.zeromail.core.rules.model.ActionProposal;
import com.zeromail.core.rules.model.MatcherNode;
import com.zeromail.core.rules.model.RuleConflictType;
import com.zeromail.core.rules.model.RuleEvaluationInput;
import com.zeromail.core.rules.service.ActionProposalMerger.ActionProposalMergeResult;
import com.zeromail.core.rules.service.ActionProposalMerger.RuleActionCandidate;

class ActionProposalMergerTest {

  private final ActionProposalMerger actionProposalMerger = new ActionProposalMerger();

  @Test
  void duplicate_label_archive_and_save_draft_intents_dedupe_with_ordered_provenance() {
    UUID firstRuleId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID secondRuleId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    ActionProposalMergeResult mergeResult =
        actionProposalMerger.merge(
            List.of(
                proposal(new ActionIntent.Label("Finance"), firstRuleId, "Receipts", "sender"),
                proposal(new ActionIntent.Label("Finance"), secondRuleId, "Stripe", "subject"),
                proposal(new ActionIntent.Archive(), firstRuleId, "Receipts", "archive-source"),
                proposal(new ActionIntent.Archive(), secondRuleId, "Stripe", "archive-subject"),
                proposal(
                    new ActionIntent.SaveDraft("Draft a polite acknowledgement"),
                    firstRuleId,
                    "Receipts",
                    "draft-source"),
                proposal(
                    new ActionIntent.SaveDraft("Draft a polite acknowledgement"),
                    secondRuleId,
                    "Stripe",
                    "draft-subject")),
            previewInput());

    assertThat(mergeResult.proposals()).hasSize(3);
    assertThat(mergeResult.proposals().getFirst().contributingRuleIds())
        .containsExactly(firstRuleId, secondRuleId);
    assertThat(mergeResult.proposals().getFirst().contributingRuleNames())
        .containsExactly("Receipts", "Stripe");
    assertThat(mergeResult.proposals().getFirst().evidenceIds())
        .containsExactly("sender", "subject");
    assertThat(mergeResult.proposals().get(1).type().id()).isEqualTo("archive");
    assertThat(mergeResult.proposals().get(2).type().id()).isEqualTo("save_draft");
  }

  @Test
  void conflict_warnings_do_not_block_proposal_creation() {
    UUID archiveRuleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    UUID draftRuleId = UUID.fromString("00000000-0000-0000-0000-000000000004");

    ActionProposalMergeResult mergeResult =
        actionProposalMerger.merge(
            List.of(
                proposal(new ActionIntent.Archive(), archiveRuleId, "Archive receipts", "archive"),
                proposal(
                    new ActionIntent.SaveDraft("Draft a reply"),
                    draftRuleId,
                    "Reply needed",
                    "semantic")),
            previewInput());

    assertThat(mergeResult.proposals()).hasSize(2);
    assertThat(mergeResult.warnings())
        .extracting(warning -> warning.type().id())
        .containsExactly("archive_and_save_draft");
  }

  @Test
  void conflict_warning_metadata_has_stable_type_ids_and_safe_counts_only() {
    UUID firstRuleId = UUID.fromString("00000000-0000-0000-0000-000000000005");
    UUID secondRuleId = UUID.fromString("00000000-0000-0000-0000-000000000006");

    ActionProposalMergeResult mergeResult =
        actionProposalMerger.merge(
            List.of(
                proposal(new ActionIntent.Label("Finance"), firstRuleId, "Finance rule", "finance"),
                proposal(
                    new ActionIntent.Label("CATEGORY_SOCIAL"),
                    secondRuleId,
                    "Category rule",
                    "category")),
            previewInput());

    assertThat(mergeResult.warnings())
        .extracting(ActionProposalMerger.RuleConflictWarning::type)
        .containsExactly(
            RuleConflictType.MULTIPLE_DIFFERENT_LABELS, RuleConflictType.CATEGORY_LABEL_MISMATCH);
    assertThat(mergeResult.warnings())
        .allSatisfy(
            warning -> {
              assertThat(warning.type().id()).isNotBlank();
              assertThat(warning.metadata().values())
                  .allSatisfy(
                      metadataValue ->
                          assertThat(metadataValue)
                              .doesNotContain("Receipt")
                              .doesNotContain("billing@stripe.com")
                              .doesNotContain("Finance")
                              .doesNotContain("CATEGORY_SOCIAL"));
            });
  }

  @Test
  void evaluate_and_merge_uses_rule_order_and_includes_only_enabled_or_previewed_disabled_rules() {
    UUID firstRuleId = UUID.fromString("00000000-0000-0000-0000-000000000007");
    UUID secondRuleId = UUID.fromString("00000000-0000-0000-0000-000000000008");
    UUID ignoredRuleId = UUID.fromString("00000000-0000-0000-0000-000000000009");

    ActionProposalMergeResult mergeResult =
        actionProposalMerger.evaluateAndMerge(
            List.of(
                candidate(secondRuleId, "Second", 2, true, false, new ActionIntent.Archive()),
                candidate(
                    firstRuleId, "Previewed disabled", 1, false, true, new ActionIntent.Archive()),
                candidate(
                    ignoredRuleId,
                    "Ignored disabled",
                    0,
                    false,
                    false,
                    new ActionIntent.Label("Ignored"))),
            previewInput());

    assertThat(mergeResult.proposals()).hasSize(1);
    assertThat(mergeResult.proposals().getFirst().contributingRuleIds())
        .containsExactly(firstRuleId, secondRuleId);
    assertThat(mergeResult.proposals().getFirst().contributingRuleNames())
        .containsExactly("Previewed disabled", "Second");
  }

  @Test
  void duplicate_draft_intents_emit_warning_after_dedupe() {
    UUID firstRuleId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    UUID secondRuleId = UUID.fromString("00000000-0000-0000-0000-000000000011");

    ActionProposalMergeResult mergeResult =
        actionProposalMerger.merge(
            List.of(
                proposal(
                    new ActionIntent.SaveDraft("Draft a reply"), firstRuleId, "First", "first"),
                proposal(
                    new ActionIntent.SaveDraft("Draft a reply"), secondRuleId, "Second", "second")),
            previewInput());

    assertThat(mergeResult.proposals()).hasSize(1);
    assertThat(mergeResult.warnings())
        .extracting(ActionProposalMerger.RuleConflictWarning::type)
        .containsExactly(RuleConflictType.DUPLICATE_DRAFT_INTENT);
  }

  private static RuleActionCandidate candidate(
      UUID ruleId,
      String ruleName,
      int ruleOrder,
      boolean enabled,
      boolean includeDisabledRuleForPreview,
      ActionIntent actionIntent) {
    return new RuleActionCandidate(
        ruleId,
        ruleName,
        ruleOrder,
        enabled,
        includeDisabledRuleForPreview,
        new MatcherNode.SenderDomainMatcher("sender-domain-" + ruleOrder, "stripe.com"),
        List.of(actionIntent));
  }

  private static ActionProposal proposal(
      ActionIntent actionIntent, UUID ruleId, String ruleName, String evidenceId) {
    return new ActionProposal(
        actionIntent, List.of(ruleId), List.of(ruleName), List.of(evidenceId));
  }

  private static RuleEvaluationInput previewInput() {
    return new RuleEvaluationInput(
        "billing@stripe.com",
        "stripe.com",
        List.of("founder@example.test"),
        List.of(),
        "Receipt from Stripe",
        List.of("INBOX"),
        List.of("promotions"),
        Instant.parse("2026-05-08T10:00:00Z"),
        Instant.parse("2026-05-09T10:00:00Z"),
        false,
        false,
        false,
        Optional.empty(),
        Set.of());
  }
}
