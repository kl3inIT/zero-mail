package com.zeromail.core.rules.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RulePreviewResult(
    ImpactSummary impactSummary, List<PreviewRow> rows, boolean savedRuleMarkedPreviewed) {

  public RulePreviewResult {
    Objects.requireNonNull(impactSummary, "impactSummary must not be null");
    rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
  }

  public record ImpactSummary(
      int sampleSize,
      int sampledMessageCount,
      int matchedCount,
      Map<String, Integer> proposedActionCounts,
      int deferredCount,
      int conflictCount,
      boolean noWriteNotice,
      String noWriteNoticeKey) {

    public ImpactSummary {
      proposedActionCounts =
          Map.copyOf(
              Objects.requireNonNull(
                  proposedActionCounts, "proposedActionCounts must not be null"));
      noWriteNoticeKey =
          Objects.requireNonNull(noWriteNoticeKey, "noWriteNoticeKey must not be null");
    }
  }

  public record PreviewRow(
      String gmailMessageId,
      String gmailThreadId,
      String sanitizedSenderEmail,
      String sanitizedSenderDomain,
      String sanitizedSubjectExcerpt,
      Instant internalDate,
      List<String> gmailLabelIds,
      boolean matched,
      List<ActionChip> proposedActionChips,
      List<EvidenceChip> matchedEvidenceChips,
      List<EvidenceChip> deferredEvidenceChips,
      List<ConflictChip> conflictChips) {

    public PreviewRow {
      Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
      Objects.requireNonNull(gmailThreadId, "gmailThreadId must not be null");
      sanitizedSenderEmail = Objects.requireNonNullElse(sanitizedSenderEmail, "");
      sanitizedSenderDomain = Objects.requireNonNullElse(sanitizedSenderDomain, "");
      sanitizedSubjectExcerpt = Objects.requireNonNullElse(sanitizedSubjectExcerpt, "");
      Objects.requireNonNull(internalDate, "internalDate must not be null");
      gmailLabelIds =
          List.copyOf(Objects.requireNonNull(gmailLabelIds, "gmailLabelIds must not be null"));
      proposedActionChips =
          List.copyOf(
              Objects.requireNonNull(proposedActionChips, "proposedActionChips must not be null"));
      matchedEvidenceChips =
          List.copyOf(
              Objects.requireNonNull(
                  matchedEvidenceChips, "matchedEvidenceChips must not be null"));
      deferredEvidenceChips =
          List.copyOf(
              Objects.requireNonNull(
                  deferredEvidenceChips, "deferredEvidenceChips must not be null"));
      conflictChips =
          List.copyOf(Objects.requireNonNull(conflictChips, "conflictChips must not be null"));
    }
  }

  public record ActionChip(
      String actionTypeId,
      String safeLabel,
      List<UUID> contributingRuleIds,
      List<String> evidenceIds) {

    public ActionChip {
      Objects.requireNonNull(actionTypeId, "actionTypeId must not be null");
      safeLabel = Objects.requireNonNullElse(safeLabel, actionTypeId);
      contributingRuleIds =
          List.copyOf(
              Objects.requireNonNull(contributingRuleIds, "contributingRuleIds must not be null"));
      evidenceIds =
          List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds must not be null"));
    }
  }

  public record EvidenceChip(String matcherNodeId, String reasonKey) {

    public EvidenceChip {
      Objects.requireNonNull(matcherNodeId, "matcherNodeId must not be null");
      Objects.requireNonNull(reasonKey, "reasonKey must not be null");
    }
  }

  public record ConflictChip(
      String conflictTypeId, List<UUID> contributingRuleIds, Map<String, String> metadata) {

    public ConflictChip {
      Objects.requireNonNull(conflictTypeId, "conflictTypeId must not be null");
      contributingRuleIds =
          List.copyOf(
              Objects.requireNonNull(contributingRuleIds, "contributingRuleIds must not be null"));
      metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    }
  }
}
