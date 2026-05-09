package com.zeromail.core.rules.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.zeromail.core.rules.model.ActionIntent;
import com.zeromail.core.rules.model.ActionProposal;
import com.zeromail.core.rules.model.MatcherEvaluationState;
import com.zeromail.core.rules.model.MatcherNode;
import com.zeromail.core.rules.model.RuleConflictType;
import com.zeromail.core.rules.model.RuleEvaluationInput;
import com.zeromail.core.rules.model.RuleEvaluationResult;

public class ActionProposalMerger {

  private static final Set<String> GMAIL_CATEGORY_NAMES =
      Set.of("primary", "promotions", "social", "updates", "forums");

  private final RuleEvaluator ruleEvaluator;

  public ActionProposalMerger() {
    this(new RuleEvaluator());
  }

  public ActionProposalMerger(RuleEvaluator ruleEvaluator) {
    this.ruleEvaluator = Objects.requireNonNull(ruleEvaluator, "ruleEvaluator must not be null");
  }

  public ActionProposalMergeResult evaluateAndMerge(
      List<RuleActionCandidate> ruleActionCandidates, RuleEvaluationInput ruleEvaluationInput) {
    List<RuleActionCandidate> orderedCandidates =
        ruleActionCandidates.stream()
            .filter(
                ruleActionCandidate ->
                    ruleActionCandidate.enabled()
                        || ruleActionCandidate.includeDisabledRuleForPreview())
            .sorted(Comparator.comparingInt(RuleActionCandidate::ruleOrder))
            .toList();
    ArrayList<ActionProposal> orderedProposals = new ArrayList<>();

    for (RuleActionCandidate ruleActionCandidate : orderedCandidates) {
      RuleEvaluationResult evaluationResult =
          ruleEvaluator.evaluate(ruleActionCandidate.matcherNode(), ruleEvaluationInput);
      if (evaluationResult.status() != MatcherEvaluationState.MATCHED) {
        continue;
      }
      for (ActionIntent actionIntent : ruleActionCandidate.actionIntents()) {
        orderedProposals.add(
            new ActionProposal(
                actionIntent,
                List.of(ruleActionCandidate.ruleId()),
                List.of(ruleActionCandidate.ruleName()),
                evaluationResult.matchedEvidenceIds()));
      }
    }

    return merge(orderedProposals, ruleEvaluationInput);
  }

  public ActionProposalMergeResult merge(
      List<ActionProposal> orderedProposals, RuleEvaluationInput ruleEvaluationInput) {
    List<ActionProposal> safeOrderedProposals =
        List.copyOf(Objects.requireNonNull(orderedProposals, "orderedProposals must not be null"));
    LinkedHashMap<ActionIntent, ActionProposal> proposalsByActionIntent = new LinkedHashMap<>();
    for (ActionProposal orderedProposal : safeOrderedProposals) {
      ActionProposal existingProposal = proposalsByActionIntent.get(orderedProposal.actionIntent());
      if (existingProposal == null) {
        proposalsByActionIntent.put(orderedProposal.actionIntent(), orderedProposal);
      } else {
        proposalsByActionIntent.put(
            orderedProposal.actionIntent(), existingProposal.mergeDuplicate(orderedProposal));
      }
    }

    List<ActionProposal> mergedProposals = List.copyOf(proposalsByActionIntent.values());
    List<RuleConflictWarning> conflictWarnings =
        detectConflictWarnings(safeOrderedProposals, ruleEvaluationInput);
    return new ActionProposalMergeResult(mergedProposals, conflictWarnings);
  }

  private static List<RuleConflictWarning> detectConflictWarnings(
      List<ActionProposal> orderedProposals, RuleEvaluationInput ruleEvaluationInput) {
    ArrayList<RuleConflictWarning> conflictWarnings = new ArrayList<>();
    appendMultipleDifferentLabelsWarning(orderedProposals, conflictWarnings);
    appendArchiveAndSaveDraftWarning(orderedProposals, conflictWarnings);
    appendDuplicateDraftIntentWarning(orderedProposals, conflictWarnings);
    appendCategoryLabelMismatchWarning(orderedProposals, ruleEvaluationInput, conflictWarnings);
    return List.copyOf(conflictWarnings);
  }

  private static void appendMultipleDifferentLabelsWarning(
      List<ActionProposal> orderedProposals, List<RuleConflictWarning> conflictWarnings) {
    LinkedHashSet<String> labelNames = new LinkedHashSet<>();
    ArrayList<ActionProposal> labelProposals = new ArrayList<>();
    for (ActionProposal orderedProposal : orderedProposals) {
      if (orderedProposal.actionIntent() instanceof ActionIntent.Label(String labelName)) {
        labelNames.add(labelName);
        labelProposals.add(orderedProposal);
      }
    }
    if (labelNames.size() > 1) {
      conflictWarnings.add(
          new RuleConflictWarning(
              RuleConflictType.MULTIPLE_DIFFERENT_LABELS,
              contributingRuleIds(labelProposals),
              safeCountMetadata("distinctLabelCount", labelNames.size())));
    }
  }

  private static void appendArchiveAndSaveDraftWarning(
      List<ActionProposal> orderedProposals, List<RuleConflictWarning> conflictWarnings) {
    List<ActionProposal> archiveProposals =
        proposalsOfType(orderedProposals, ActionIntent.Archive.class);
    List<ActionProposal> draftProposals =
        proposalsOfType(orderedProposals, ActionIntent.SaveDraft.class);
    if (!archiveProposals.isEmpty() && !draftProposals.isEmpty()) {
      ArrayList<ActionProposal> conflictProposals = new ArrayList<>(archiveProposals);
      conflictProposals.addAll(draftProposals);
      conflictWarnings.add(
          new RuleConflictWarning(
              RuleConflictType.ARCHIVE_AND_SAVE_DRAFT,
              contributingRuleIds(conflictProposals),
              safeCountMetadata("proposalCount", conflictProposals.size())));
    }
  }

  private static void appendDuplicateDraftIntentWarning(
      List<ActionProposal> orderedProposals, List<RuleConflictWarning> conflictWarnings) {
    List<ActionProposal> draftProposals =
        proposalsOfType(orderedProposals, ActionIntent.SaveDraft.class);
    if (draftProposals.size() > 1) {
      conflictWarnings.add(
          new RuleConflictWarning(
              RuleConflictType.DUPLICATE_DRAFT_INTENT,
              contributingRuleIds(draftProposals),
              safeCountMetadata("draftProposalCount", draftProposals.size())));
    }
  }

  private static void appendCategoryLabelMismatchWarning(
      List<ActionProposal> orderedProposals,
      RuleEvaluationInput ruleEvaluationInput,
      List<RuleConflictWarning> conflictWarnings) {
    LinkedHashSet<String> proposedCategoryLabels = new LinkedHashSet<>();
    ArrayList<ActionProposal> categoryLabelProposals = new ArrayList<>();
    for (ActionProposal orderedProposal : orderedProposals) {
      if (orderedProposal.actionIntent() instanceof ActionIntent.Label(String labelName)) {
        Optional<String> proposedCategoryLabel = normalizeCategoryLabel(labelName);
        proposedCategoryLabel.ifPresent(proposedCategoryLabels::add);
        if (proposedCategoryLabel.isPresent()) {
          categoryLabelProposals.add(orderedProposal);
        }
      }
    }
    if (proposedCategoryLabels.isEmpty() || ruleEvaluationInput.gmailCategories().isEmpty()) {
      return;
    }
    boolean hasMismatch =
        proposedCategoryLabels.stream()
            .anyMatch(
                proposedCategoryLabel ->
                    !ruleEvaluationInput.hasGmailCategory(proposedCategoryLabel));
    if (hasMismatch) {
      LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
      metadata.put(
          "currentCategoryCount", String.valueOf(ruleEvaluationInput.gmailCategories().size()));
      metadata.put("proposedCategoryCount", String.valueOf(proposedCategoryLabels.size()));
      conflictWarnings.add(
          new RuleConflictWarning(
              RuleConflictType.CATEGORY_LABEL_MISMATCH,
              contributingRuleIds(categoryLabelProposals),
              metadata));
    }
  }

  private static <T extends ActionIntent> List<ActionProposal> proposalsOfType(
      List<ActionProposal> orderedProposals, Class<T> actionIntentClass) {
    return orderedProposals.stream()
        .filter(orderedProposal -> actionIntentClass.isInstance(orderedProposal.actionIntent()))
        .toList();
  }

  private static Optional<String> normalizeCategoryLabel(String labelName) {
    String normalizedLabel = labelName.trim().toLowerCase(Locale.ROOT);
    if (normalizedLabel.startsWith("category_")) {
      normalizedLabel = normalizedLabel.substring("category_".length());
    }
    if (GMAIL_CATEGORY_NAMES.contains(normalizedLabel)) {
      return Optional.of(normalizedLabel);
    }
    return Optional.empty();
  }

  private static List<UUID> contributingRuleIds(List<ActionProposal> actionProposals) {
    ArrayList<UUID> contributingRuleIds = new ArrayList<>();
    for (ActionProposal actionProposal : actionProposals) {
      for (UUID contributingRuleId : actionProposal.contributingRuleIds()) {
        if (!contributingRuleIds.contains(contributingRuleId)) {
          contributingRuleIds.add(contributingRuleId);
        }
      }
    }
    return contributingRuleIds;
  }

  private static Map<String, String> safeCountMetadata(String key, int value) {
    return Map.of(key, String.valueOf(value));
  }

  public record RuleActionCandidate(
      UUID ruleId,
      String ruleName,
      int ruleOrder,
      boolean enabled,
      boolean includeDisabledRuleForPreview,
      MatcherNode matcherNode,
      List<ActionIntent> actionIntents) {

    public RuleActionCandidate {
      Objects.requireNonNull(ruleId, "ruleId must not be null");
      if (ruleName == null || ruleName.isBlank()) {
        throw new IllegalArgumentException("ruleName must not be blank");
      }
      Objects.requireNonNull(matcherNode, "matcherNode must not be null");
      actionIntents =
          List.copyOf(Objects.requireNonNull(actionIntents, "actionIntents must not be null"));
      if (actionIntents.isEmpty()) {
        throw new IllegalArgumentException("actionIntents must not be empty");
      }
    }
  }

  public record ActionProposalMergeResult(
      List<ActionProposal> proposals, List<RuleConflictWarning> warnings) {

    public ActionProposalMergeResult {
      proposals = List.copyOf(Objects.requireNonNull(proposals, "proposals must not be null"));
      warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
  }

  public record RuleConflictWarning(
      RuleConflictType type, List<UUID> contributingRuleIds, Map<String, String> metadata) {

    public RuleConflictWarning {
      Objects.requireNonNull(type, "type must not be null");
      contributingRuleIds =
          List.copyOf(
              Objects.requireNonNull(contributingRuleIds, "contributingRuleIds must not be null"));
      metadata =
          Collections.unmodifiableMap(
              new LinkedHashMap<>(Objects.requireNonNull(metadata, "metadata must not be null")));
      if (contributingRuleIds.isEmpty()) {
        throw new IllegalArgumentException("contributingRuleIds must not be empty");
      }
    }
  }
}
