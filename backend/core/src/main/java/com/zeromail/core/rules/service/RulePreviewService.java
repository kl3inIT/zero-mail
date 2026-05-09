package com.zeromail.core.rules.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.rules.model.ActionIntent;
import com.zeromail.core.rules.model.ActionProposal;
import com.zeromail.core.rules.model.MatcherEvaluationState;
import com.zeromail.core.rules.model.MatcherNode;
import com.zeromail.core.rules.model.MatcherType;
import com.zeromail.core.rules.model.PreviewSampleSize;
import com.zeromail.core.rules.model.RuleActionType;
import com.zeromail.core.rules.model.RulePreviewCommand;
import com.zeromail.core.rules.model.RulePreviewResult;
import com.zeromail.core.rules.model.RuleValidationException;
import com.zeromail.core.rules.model.SemanticIntentMatcher;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RulePreviewService {

  private static final UUID DRAFT_RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final String NO_WRITE_NOTICE_KEY = "rules.preview.noGmailChanges";

  private final RuleRepository ruleRepository;
  private final RuleManagementService ruleManagementService;
  private final RulePreviewDataService rulePreviewDataService;
  private final RuleEvaluator ruleEvaluator;
  private final ActionProposalMerger actionProposalMerger;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Autowired
  public RulePreviewService(
      RuleRepository ruleRepository,
      RuleManagementService ruleManagementService,
      RulePreviewDataService rulePreviewDataService) {
    this(
        ruleRepository,
        ruleManagementService,
        rulePreviewDataService,
        new RuleEvaluator(),
        new ActionProposalMerger(),
        JsonMapper.builder().build(),
        Clock.systemUTC());
  }

  RulePreviewService(
      RuleRepository ruleRepository,
      RuleManagementService ruleManagementService,
      RulePreviewDataService rulePreviewDataService,
      RuleEvaluator ruleEvaluator,
      ActionProposalMerger actionProposalMerger,
      ObjectMapper objectMapper,
      Clock clock) {
    this.ruleRepository = Objects.requireNonNull(ruleRepository, "ruleRepository must not be null");
    this.ruleManagementService =
        Objects.requireNonNull(ruleManagementService, "ruleManagementService must not be null");
    this.rulePreviewDataService =
        Objects.requireNonNull(rulePreviewDataService, "rulePreviewDataService must not be null");
    this.ruleEvaluator = Objects.requireNonNull(ruleEvaluator, "ruleEvaluator must not be null");
    this.actionProposalMerger =
        Objects.requireNonNull(actionProposalMerger, "actionProposalMerger must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public int normalizeSampleSize(Integer requestedSampleSize) {
    return PreviewSampleSize.normalize(requestedSampleSize).value();
  }

  @Transactional
  public RulePreviewResult previewSavedRule(
      UUID tenantId, UUID ruleId, Integer requestedSampleSize) {
    return preview(RulePreviewCommand.savedRule(tenantId, ruleId, requestedSampleSize));
  }

  @Transactional(readOnly = true)
  public RulePreviewResult previewDraft(
      UUID tenantId,
      MatcherNode matcherNode,
      List<ActionIntent> actionIntents,
      Integer requestedSampleSize) {
    return preview(
        RulePreviewCommand.draft(tenantId, matcherNode, actionIntents, requestedSampleSize));
  }

  @Transactional(readOnly = true)
  public RulePreviewResult previewDraft(
      UUID tenantId, String matcherAst, String actionIntents, Integer requestedSampleSize) {
    return previewDraft(
        tenantId, parseMatcher(matcherAst), parseActionIntents(actionIntents), requestedSampleSize);
  }

  @Transactional
  public RulePreviewResult preview(RulePreviewCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    PreviewSampleSize sampleSize = PreviewSampleSize.normalize(command.requestedSampleSize());
    PreviewTarget previewTarget =
        command.savedRulePreview() ? savedPreviewTarget(command) : draftPreviewTarget(command);
    boolean requiresBodyEvidence =
        previewTarget.candidates().stream()
            .anyMatch(previewCandidate -> previewCandidate.matcherNode().requiresBodyEvidence());
    List<RulePreviewDataService.PreviewInput> previewInputs =
        rulePreviewDataService.fetchPreviewInputs(
            command.tenantId(), requiresBodyEvidence, sampleSize);
    RulePreviewResult result =
        buildResult(sampleSize, previewTarget.candidates(), previewInputs, false);
    if (command.savedRulePreview()) {
      ruleManagementService.markPreviewSucceeded(
          command.tenantId(),
          command.ruleId(),
          previewTarget.savedRuleEntityVersion(),
          Instant.now(clock));
      return new RulePreviewResult(result.impactSummary(), result.rows(), true);
    }
    return result;
  }

  private PreviewTarget savedPreviewTarget(RulePreviewCommand command) {
    List<RuleEntity> orderedRules = ruleRepository.findOrderedByTenantId(command.tenantId());
    RuleEntity currentRule =
        orderedRules.stream()
            .filter(ruleEntity -> ruleEntity.getId().equals(command.ruleId()))
            .findFirst()
            .orElseThrow(RuleValidationException::notFound);

    ArrayList<PreviewCandidate> previewCandidates = new ArrayList<>();
    for (RuleEntity ruleEntity : orderedRules) {
      boolean currentRuleForPreview = ruleEntity.getId().equals(command.ruleId());
      if (!ruleEntity.isEnabled() && !currentRuleForPreview) {
        continue;
      }
      previewCandidates.add(toPreviewCandidate(ruleEntity, currentRuleForPreview));
    }
    return new PreviewTarget(previewCandidates, currentRule.getEntityVersion());
  }

  private PreviewTarget draftPreviewTarget(RulePreviewCommand command) {
    return new PreviewTarget(
        List.of(
            new PreviewCandidate(
                DRAFT_RULE_ID,
                "Draft rule",
                0,
                true,
                true,
                command.matcherNode(),
                command.actionIntents())),
        null);
  }

  private PreviewCandidate toPreviewCandidate(
      RuleEntity ruleEntity, boolean includeDisabledRuleForPreview) {
    return new PreviewCandidate(
        ruleEntity.getId(),
        ruleEntity.getDisplayName(),
        ruleEntity.getOrderIndex(),
        ruleEntity.isEnabled(),
        includeDisabledRuleForPreview,
        parseMatcher(ruleEntity.getMatcherAst()),
        parseActionIntents(ruleEntity.getActionIntents()));
  }

  private RulePreviewResult buildResult(
      PreviewSampleSize sampleSize,
      List<PreviewCandidate> previewCandidates,
      List<RulePreviewDataService.PreviewInput> previewInputs,
      boolean savedRuleMarkedPreviewed) {
    ArrayList<RulePreviewResult.PreviewRow> rows = new ArrayList<>();
    LinkedHashMap<String, Integer> actionCounts = new LinkedHashMap<>();
    int matchedCount = 0;
    int deferredCount = 0;
    int conflictCount = 0;

    for (RulePreviewDataService.PreviewInput previewInput : previewInputs) {
      RowEvaluation rowEvaluation = evaluateRow(previewCandidates, previewInput);
      if (!rowEvaluation.actionChips().isEmpty()) {
        matchedCount++;
      }
      if (!rowEvaluation.deferredEvidenceChips().isEmpty()) {
        deferredCount++;
      }
      conflictCount += rowEvaluation.conflictChips().size();
      for (RulePreviewResult.ActionChip actionChip : rowEvaluation.actionChips()) {
        actionCounts.merge(actionChip.actionTypeId(), 1, Integer::sum);
      }
      rows.add(
          new RulePreviewResult.PreviewRow(
              previewInput.gmailMessageId(),
              previewInput.gmailThreadId(),
              previewInput.summary().sanitizedSenderEmail(),
              previewInput.summary().sanitizedSenderDomain(),
              previewInput.summary().sanitizedSubjectExcerpt(),
              previewInput.summary().internalDate(),
              previewInput.summary().gmailLabelIds(),
              !rowEvaluation.actionChips().isEmpty(),
              rowEvaluation.actionChips(),
              rowEvaluation.matchedEvidenceChips(),
              rowEvaluation.deferredEvidenceChips(),
              rowEvaluation.conflictChips()));
    }

    RulePreviewResult.ImpactSummary impactSummary =
        new RulePreviewResult.ImpactSummary(
            sampleSize.value(),
            previewInputs.size(),
            matchedCount,
            actionCounts,
            deferredCount,
            conflictCount,
            true,
            NO_WRITE_NOTICE_KEY);
    return new RulePreviewResult(impactSummary, rows, savedRuleMarkedPreviewed);
  }

  private RowEvaluation evaluateRow(
      List<PreviewCandidate> previewCandidates, RulePreviewDataService.PreviewInput previewInput) {
    ArrayList<ActionProposal> orderedProposals = new ArrayList<>();
    LinkedHashMap<String, String> matchedEvidenceById = new LinkedHashMap<>();
    LinkedHashMap<String, String> deferredEvidenceById = new LinkedHashMap<>();

    List<PreviewCandidate> orderedCandidates =
        previewCandidates.stream()
            .filter(candidate -> candidate.enabled() || candidate.includeDisabledRuleForPreview())
            .sorted(java.util.Comparator.comparingInt(PreviewCandidate::orderIndex))
            .toList();
    for (PreviewCandidate previewCandidate : orderedCandidates) {
      var evaluationResult =
          ruleEvaluator.evaluate(
              previewCandidate.matcherNode(), previewInput.ruleEvaluationInput());
      if (evaluationResult.status() == MatcherEvaluationState.MATCHED) {
        for (String matchedEvidenceId : evaluationResult.matchedEvidenceIds()) {
          matchedEvidenceById.put(
              matchedEvidenceId, evaluationResult.evidenceById().get(matchedEvidenceId));
        }
        for (ActionIntent actionIntent : previewCandidate.actionIntents()) {
          orderedProposals.add(
              new ActionProposal(
                  actionIntent,
                  List.of(previewCandidate.ruleId()),
                  List.of(previewCandidate.displayName()),
                  evaluationResult.matchedEvidenceIds()));
        }
      } else if (evaluationResult.status() == MatcherEvaluationState.DEFERRED) {
        for (String deferredEvidenceId : evaluationResult.deferredEvidenceIds()) {
          deferredEvidenceById.put(
              deferredEvidenceId, evaluationResult.evidenceById().get(deferredEvidenceId));
        }
      }
    }

    ActionProposalMerger.ActionProposalMergeResult mergeResult =
        actionProposalMerger.merge(orderedProposals, previewInput.ruleEvaluationInput());
    return new RowEvaluation(
        mergeResult.proposals().stream().map(RulePreviewService::toActionChip).toList(),
        toEvidenceChips(matchedEvidenceById),
        toEvidenceChips(deferredEvidenceById),
        mergeResult.warnings().stream().map(RulePreviewService::toConflictChip).toList());
  }

  private static RulePreviewResult.ActionChip toActionChip(ActionProposal actionProposal) {
    return new RulePreviewResult.ActionChip(
        actionProposal.type().id(),
        safeActionLabel(actionProposal.actionIntent()),
        actionProposal.contributingRuleIds(),
        actionProposal.evidenceIds());
  }

  private static String safeActionLabel(ActionIntent actionIntent) {
    return switch (actionIntent) {
      case ActionIntent.Label label -> "label:" + label.labelName();
      case ActionIntent.Archive ignored -> "archive";
      case ActionIntent.SaveDraft ignored -> "save_draft";
    };
  }

  private static List<RulePreviewResult.EvidenceChip> toEvidenceChips(
      LinkedHashMap<String, String> evidenceById) {
    return evidenceById.entrySet().stream()
        .map(entry -> new RulePreviewResult.EvidenceChip(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static RulePreviewResult.ConflictChip toConflictChip(
      ActionProposalMerger.RuleConflictWarning warning) {
    return new RulePreviewResult.ConflictChip(
        warning.type().id(), warning.contributingRuleIds(), warning.metadata());
  }

  private MatcherNode parseMatcher(String matcherJson) {
    try {
      return parseMatcherNode(objectMapper.readTree(matcherJson), "matcher");
    } catch (JacksonException jacksonException) {
      throw new IllegalArgumentException("matcherJson must be valid JSON", jacksonException);
    }
  }

  private MatcherNode parseMatcherNode(JsonNode matcherNode, String fallbackNodeId) {
    MatcherType matcherType = MatcherType.fromId(text(matcherNode, "type", "matcherType"));
    String nodeId = optionalText(matcherNode, "nodeId", fallbackNodeId);
    return switch (matcherType) {
      case SENDER_EMAIL -> new MatcherNode.SenderEmailMatcher(nodeId, text(matcherNode, "email"));
      case SENDER_DOMAIN ->
          new MatcherNode.SenderDomainMatcher(nodeId, text(matcherNode, "domain"));
      case RECIPIENT_TO -> new MatcherNode.RecipientToMatcher(nodeId, text(matcherNode, "email"));
      case RECIPIENT_CC -> new MatcherNode.RecipientCcMatcher(nodeId, text(matcherNode, "email"));
      case SUBJECT_CONTAINS ->
          new MatcherNode.SubjectContainsMatcher(nodeId, text(matcherNode, "text", "value"));
      case SUBJECT_EQUALS ->
          new MatcherNode.SubjectEqualsMatcher(nodeId, text(matcherNode, "text", "value"));
      case SUBJECT_REGEX ->
          new MatcherNode.SubjectRegexMatcher(nodeId, text(matcherNode, "regexPattern", "pattern"));
      case GMAIL_LABEL_PRESENT ->
          new MatcherNode.GmailLabelPresentMatcher(nodeId, text(matcherNode, "labelId"));
      case GMAIL_LABEL_ABSENT ->
          new MatcherNode.GmailLabelAbsentMatcher(nodeId, text(matcherNode, "labelId"));
      case GMAIL_CATEGORY_PRESENT ->
          new MatcherNode.GmailCategoryPresentMatcher(nodeId, text(matcherNode, "category"));
      case GMAIL_CATEGORY_ABSENT ->
          new MatcherNode.GmailCategoryAbsentMatcher(nodeId, text(matcherNode, "category"));
      case HAS_ATTACHMENT -> new MatcherNode.HasAttachmentMatcher(nodeId);
      case LIST_UNSUBSCRIBE_PRESENT -> new MatcherNode.ListUnsubscribePresentMatcher(nodeId);
      case NEWSLETTER_INDICATOR -> new MatcherNode.NewsletterIndicatorMatcher(nodeId);
      case MESSAGE_AGE ->
          new MatcherNode.MessageAgeMatcher(
              nodeId,
              MatcherNode.MessageAgeOperator.valueOf(text(matcherNode, "operator")),
              intValue(matcherNode, "days"));
      case MESSAGE_DATE ->
          new MatcherNode.MessageDateMatcher(
              nodeId,
              MatcherNode.MessageDateOperator.valueOf(text(matcherNode, "operator")),
              LocalDate.parse(text(matcherNode, "date")));
      case ALL -> new MatcherNode.AllMatcher(nodeId, parseChildren(matcherNode, nodeId));
      case ANY -> new MatcherNode.AnyMatcher(nodeId, parseChildren(matcherNode, nodeId));
      case NOT ->
          new MatcherNode.NotMatcher(
              nodeId, parseMatcherNode(required(matcherNode, "child"), nodeId + "-child"));
      case SEMANTIC_INTENT ->
          new SemanticIntentMatcher(
              nodeId,
              text(matcherNode, "description", "intent"),
              booleanValue(matcherNode, "deferred"));
    };
  }

  private List<MatcherNode> parseChildren(JsonNode matcherNode, String nodeId) {
    JsonNode childrenNode = required(matcherNode, "children");
    ArrayList<MatcherNode> children = new ArrayList<>();
    for (int childIndex = 0; childIndex < childrenNode.size(); childIndex++) {
      children.add(parseMatcherNode(childrenNode.get(childIndex), nodeId + "-" + childIndex));
    }
    return List.copyOf(children);
  }

  private List<ActionIntent> parseActionIntents(String actionIntentsJson) {
    try {
      JsonNode rootNode = objectMapper.readTree(actionIntentsJson);
      ArrayList<ActionIntent> actionIntents = new ArrayList<>();
      for (JsonNode actionIntentNode : rootNode) {
        RuleActionType actionType = RuleActionType.fromId(text(actionIntentNode, "type", "action"));
        actionIntents.add(
            switch (actionType) {
              case LABEL -> new ActionIntent.Label(text(actionIntentNode, "labelName", "label"));
              case ARCHIVE -> new ActionIntent.Archive();
              case SAVE_DRAFT ->
                  new ActionIntent.SaveDraft(text(actionIntentNode, "instruction", "draftIntent"));
            });
      }
      return List.copyOf(actionIntents);
    } catch (JacksonException jacksonException) {
      throw new IllegalArgumentException("actionIntentsJson must be valid JSON", jacksonException);
    }
  }

  private static JsonNode required(JsonNode jsonNode, String fieldName) {
    JsonNode fieldNode = jsonNode.path(fieldName);
    if (fieldNode.isMissingNode() || fieldNode.isNull()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return fieldNode;
  }

  private static String text(JsonNode jsonNode, String fieldName) {
    return text(jsonNode, fieldName, fieldName);
  }

  private static String text(JsonNode jsonNode, String primaryFieldName, String fallbackFieldName) {
    JsonNode fieldNode = jsonNode.path(primaryFieldName);
    if ((fieldNode.isMissingNode() || fieldNode.isNull())
        && !primaryFieldName.equals(fallbackFieldName)) {
      fieldNode = jsonNode.path(fallbackFieldName);
    }
    if (fieldNode.isMissingNode() || fieldNode.isNull() || !fieldNode.isValueNode()) {
      throw new IllegalArgumentException(primaryFieldName + " is required");
    }
    return fieldNode.asString();
  }

  private static String optionalText(JsonNode jsonNode, String fieldName, String fallbackValue) {
    JsonNode fieldNode = jsonNode.path(fieldName);
    if (fieldNode.isMissingNode() || fieldNode.isNull() || !fieldNode.isValueNode()) {
      return fallbackValue;
    }
    return fieldNode.asString();
  }

  private static int intValue(JsonNode jsonNode, String fieldName) {
    JsonNode fieldNode = required(jsonNode, fieldName);
    if (!fieldNode.canConvertToInt()) {
      throw new IllegalArgumentException(fieldName + " must be an integer");
    }
    return fieldNode.asInt();
  }

  private static boolean booleanValue(JsonNode jsonNode, String fieldName) {
    JsonNode fieldNode = required(jsonNode, fieldName);
    if (!fieldNode.isBoolean()) {
      throw new IllegalArgumentException(fieldName + " must be a boolean");
    }
    return fieldNode.asBoolean();
  }

  private record PreviewTarget(List<PreviewCandidate> candidates, Integer savedRuleEntityVersion) {

    private PreviewTarget {
      candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
    }
  }

  private record PreviewCandidate(
      UUID ruleId,
      String displayName,
      int orderIndex,
      boolean enabled,
      boolean includeDisabledRuleForPreview,
      MatcherNode matcherNode,
      List<ActionIntent> actionIntents) {

    private PreviewCandidate {
      Objects.requireNonNull(ruleId, "ruleId must not be null");
      Objects.requireNonNull(displayName, "displayName must not be null");
      Objects.requireNonNull(matcherNode, "matcherNode must not be null");
      actionIntents =
          List.copyOf(Objects.requireNonNull(actionIntents, "actionIntents must not be null"));
    }
  }

  private record RowEvaluation(
      List<RulePreviewResult.ActionChip> actionChips,
      List<RulePreviewResult.EvidenceChip> matchedEvidenceChips,
      List<RulePreviewResult.EvidenceChip> deferredEvidenceChips,
      List<RulePreviewResult.ConflictChip> conflictChips) {}
}
