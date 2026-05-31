package com.zeromail.core.rules.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.SemanticIntentRequest;
import com.zeromail.core.rules.domain.ActionIntent;
import com.zeromail.core.rules.domain.ActionProposal;
import com.zeromail.core.rules.domain.ActionProposalMerger;
import com.zeromail.core.rules.domain.MatcherEvaluationState;
import com.zeromail.core.rules.domain.MatcherNode;
import com.zeromail.core.rules.domain.MatcherType;
import com.zeromail.core.rules.domain.PreviewSampleSize;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import com.zeromail.core.rules.domain.RuleEvaluationResult;
import com.zeromail.core.rules.domain.RuleEvaluator;
import com.zeromail.core.rules.domain.SemanticIntentMatcher;
import com.zeromail.core.rules.exception.RuleValidationException;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RulePreviewService {

    private static final UUID DRAFT_RULE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String NO_WRITE_NOTICE_KEY = "rules.preview.noGmailChanges";

    private final RuleRepository ruleRepository;
    private final RuleManagementService ruleManagementService;
    private final RulePreviewDataService rulePreviewDataService;
    private final RuleEvaluator ruleEvaluator;
    private final ActionProposalMerger actionProposalMerger;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LlmGateway llmGateway;

    @Autowired
    public RulePreviewService(
            RuleRepository ruleRepository,
            RuleManagementService ruleManagementService,
            RulePreviewDataService rulePreviewDataService,
            LlmGateway llmGateway) {
        this(
                ruleRepository,
                ruleManagementService,
                rulePreviewDataService,
                new RuleEvaluator(),
                new ActionProposalMerger(),
                JsonMapper.builder().build(),
                Clock.systemUTC(),
                llmGateway);
    }

    RulePreviewService(
            RuleRepository ruleRepository,
            RuleManagementService ruleManagementService,
            RulePreviewDataService rulePreviewDataService,
            RuleEvaluator ruleEvaluator,
            ActionProposalMerger actionProposalMerger,
            ObjectMapper objectMapper,
            Clock clock) {
        this(
                ruleRepository,
                ruleManagementService,
                rulePreviewDataService,
                ruleEvaluator,
                actionProposalMerger,
                objectMapper,
                clock,
                null);
    }

    RulePreviewService(
            RuleRepository ruleRepository,
            RuleManagementService ruleManagementService,
            RulePreviewDataService rulePreviewDataService,
            RuleEvaluator ruleEvaluator,
            ActionProposalMerger actionProposalMerger,
            ObjectMapper objectMapper,
            Clock clock,
            LlmGateway llmGateway) {
        this.ruleRepository =
                Objects.requireNonNull(ruleRepository, "ruleRepository must not be null");
        this.ruleManagementService =
                Objects.requireNonNull(
                        ruleManagementService, "ruleManagementService must not be null");
        this.rulePreviewDataService =
                Objects.requireNonNull(
                        rulePreviewDataService, "rulePreviewDataService must not be null");
        this.ruleEvaluator =
                Objects.requireNonNull(ruleEvaluator, "ruleEvaluator must not be null");
        this.actionProposalMerger =
                Objects.requireNonNull(
                        actionProposalMerger, "actionProposalMerger must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        // null is allowed so existing unit tests that don't exercise the
        // semantic-eval path do not need to fabricate a mock gateway.
        // preview(...) refuses to call it when null + evaluateSemanticIntents=true.
        this.llmGateway = llmGateway;
    }

    public int normalizeSampleSize(Integer requestedSampleSize) {
        return PreviewSampleSize.normalize(requestedSampleSize).value();
    }

    @Transactional
    public RulePreviewResult previewSavedRule(
            UUID tenantId, UUID ruleId, Integer requestedSampleSize) {
        return previewSavedRule(tenantId, ruleId, requestedSampleSize, false);
    }

    @Transactional
    public RulePreviewResult previewSavedRule(
            UUID tenantId,
            UUID ruleId,
            Integer requestedSampleSize,
            boolean evaluateSemanticIntents) {
        return preview(
                RulePreviewCommand.savedRule(
                        tenantId, ruleId, requestedSampleSize, evaluateSemanticIntents));
    }

    @Transactional
    public RulePreviewResult previewDraft(
            UUID tenantId,
            MatcherNode matcherNode,
            List<ActionIntent> actionIntents,
            Integer requestedSampleSize) {
        return previewDraft(tenantId, matcherNode, actionIntents, requestedSampleSize, false);
    }

    @Transactional
    public RulePreviewResult previewDraft(
            UUID tenantId,
            MatcherNode matcherNode,
            List<ActionIntent> actionIntents,
            Integer requestedSampleSize,
            boolean evaluateSemanticIntents) {
        return preview(
                RulePreviewCommand.draft(
                        tenantId,
                        matcherNode,
                        actionIntents,
                        requestedSampleSize,
                        evaluateSemanticIntents));
    }

    @Transactional
    public RulePreviewResult previewDraft(
            UUID tenantId, String matcherAst, String actionIntents, Integer requestedSampleSize) {
        return previewDraft(tenantId, matcherAst, actionIntents, requestedSampleSize, false);
    }

    /**
     * Preview against every currently-enabled rule for a tenant, with no per-rule focus and no
     * markPreviewSucceeded bookkeeping. Used by the rules /test tab where the user wants to see how
     * their current rule set behaves on real Gmail without first picking a rule.
     *
     * <p>Read-write (not {@code readOnly = true}) on purpose: when {@code evaluateSemanticIntents}
     * is set, the LLM gateway records credit-ledger consumption ({@code settle}/{@code release} run
     * with {@code Propagation.REQUIRED}, joining this transaction). A read-only transaction would
     * reject those INSERTs with "cannot execute INSERT in a read-only transaction". This matches
     * the read-write {@link #previewSavedRule} entry points.
     */
    @Transactional
    public RulePreviewResult previewAllEnabled(
            UUID tenantId, Integer requestedSampleSize, boolean evaluateSemanticIntents) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        PreviewSampleSize sampleSize = PreviewSampleSize.normalize(requestedSampleSize);
        List<RuleEntity> orderedRules = ruleRepository.findOrderedByTenantId(tenantId);
        ArrayList<PreviewCandidate> previewCandidates = new ArrayList<>();
        for (RuleEntity ruleEntity : orderedRules) {
            if (!ruleEntity.isEnabled()) {
                continue;
            }
            previewCandidates.add(toPreviewCandidate(ruleEntity, false));
        }
        if (previewCandidates.isEmpty()) {
            return new RulePreviewResult(
                    new RulePreviewResult.ImpactSummary(
                            sampleSize.value(), 0, 0, Map.of(), 0, 0, true, NO_WRITE_NOTICE_KEY),
                    List.of(),
                    false);
        }
        boolean requiresBodyEvidence =
                previewCandidates.stream()
                        .anyMatch(candidate -> candidate.matcherNode().requiresBodyEvidence());
        List<RulePreviewDataService.PreviewInput> previewInputs =
                rulePreviewDataService.fetchPreviewInputs(
                        tenantId, requiresBodyEvidence, sampleSize);
        Map<String, Map<String, Boolean>> semanticOverridesByMessage =
                evaluateSemanticIntents
                        ? resolveSemanticOverrides(previewCandidates, previewInputs)
                        : Map.of();
        return buildResult(
                sampleSize, previewCandidates, previewInputs, false, semanticOverridesByMessage);
    }

    @Transactional
    public RulePreviewResult previewDraft(
            UUID tenantId,
            String matcherAst,
            String actionIntents,
            Integer requestedSampleSize,
            boolean evaluateSemanticIntents) {
        return previewDraft(
                tenantId,
                parseMatcher(matcherAst),
                parseActionIntents(actionIntents),
                requestedSampleSize,
                evaluateSemanticIntents);
    }

    @Transactional
    public RuleCustomPreviewResult previewCustomMail(
            UUID tenantId, String subject, String body, List<UUID> requestedRuleIds) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Set<UUID> selectedRuleIds =
                requestedRuleIds == null || requestedRuleIds.isEmpty()
                        ? null
                        : Set.copyOf(requestedRuleIds);
        List<RuleEntity> orderedRules = ruleRepository.findOrderedByTenantId(tenantId);
        List<RuleEntity> targetRules =
                orderedRules.stream()
                        .filter(
                                ruleEntity ->
                                        selectedRuleIds == null
                                                ? ruleEntity.isEnabled()
                                                : selectedRuleIds.contains(ruleEntity.getId()))
                        .toList();
        if (targetRules.isEmpty()) {
            return new RuleCustomPreviewResult(List.of());
        }

        RuleEvaluationInput evaluationInput = buildCustomEvaluationInput(subject, body);
        LinkedHashMap<UUID, MatcherNode> matcherNodesByRuleId = new LinkedHashMap<>();
        for (RuleEntity ruleEntity : targetRules) {
            matcherNodesByRuleId.put(ruleEntity.getId(), parseMatcher(ruleEntity.getMatcherAst()));
        }
        // Always resolve semantic intents through the LLM so natural-language
        // conditions are decided here instead of being left "deferred" forever.
        Map<String, Boolean> semanticOverrides =
                resolveCustomSemanticOverrides(matcherNodesByRuleId.values(), subject, body);

        ArrayList<RuleCustomPreviewResult.Entry> entries = new ArrayList<>(targetRules.size());
        for (RuleEntity ruleEntity : targetRules) {
            MatcherNode matcherNode = matcherNodesByRuleId.get(ruleEntity.getId());
            List<ActionIntent> actionIntents = parseActionIntents(ruleEntity.getActionIntents());
            RuleEvaluationResult evaluationResult =
                    ruleEvaluator.evaluate(matcherNode, evaluationInput, semanticOverrides);
            boolean matched = evaluationResult.status() == MatcherEvaluationState.MATCHED;
            boolean deferred = evaluationResult.status() == MatcherEvaluationState.DEFERRED;
            entries.add(
                    new RuleCustomPreviewResult.Entry(
                            ruleEntity.getId(),
                            ruleEntity.getDisplayName(),
                            ruleEntity.isEnabled(),
                            matched,
                            deferred,
                            matched
                                    ? actionIntents.stream()
                                            .map(
                                                    actionIntent ->
                                                            toCustomActionChip(
                                                                    ruleEntity.getId(),
                                                                    actionIntent,
                                                                    evaluationResult
                                                                            .matchedEvidenceIds()))
                                            .toList()
                                    : List.of(),
                            matched
                                    ? toEvidenceChipList(
                                            evaluationResult.matchedEvidenceIds(),
                                            evaluationResult.evidenceById())
                                    : List.of(),
                            deferred
                                    ? toEvidenceChipList(
                                            evaluationResult.deferredEvidenceIds(),
                                            evaluationResult.evidenceById())
                                    : List.of()));
        }
        return new RuleCustomPreviewResult(List.copyOf(entries));
    }

    private Map<String, Boolean> resolveCustomSemanticOverrides(
            Collection<MatcherNode> matcherNodes, String subject, String body) {
        if (llmGateway == null) {
            // No LLM gateway wired (test/lite profile): leave semantic nodes
            // deferred rather than failing the deterministic preview.
            return Map.of();
        }
        LinkedHashMap<String, SemanticIntentMatcher> semanticIntentsByNodeId =
                new LinkedHashMap<>();
        for (MatcherNode matcherNode : matcherNodes) {
            collectSemanticIntentMatchers(matcherNode, semanticIntentsByNodeId);
        }
        if (semanticIntentsByNodeId.isEmpty()) {
            return Map.of();
        }
        List<SemanticIntentRequest> intents =
                semanticIntentsByNodeId.entrySet().stream()
                        .map(
                                entry ->
                                        new SemanticIntentRequest(
                                                entry.getKey(), entry.getValue().intent()))
                        .toList();
        // The custom-tester body is user-authored test input (not mail received
        // from Gmail), so it may be sent to the LLM. The gateway still sanitizes,
        // truncates, and injection-hardens the content, and never logs/stores it.
        return llmGateway.evaluateSemanticIntents(
                CallSite.PREVIEW, buildCustomSemanticContent(subject, body), intents);
    }

    private static String buildCustomSemanticContent(String subject, String body) {
        StringBuilder content = new StringBuilder();
        content.append("subject: ").append(subject == null ? "" : subject);
        content.append("\nbody: ").append(body == null ? "" : body);
        return content.toString();
    }

    private RuleEvaluationInput buildCustomEvaluationInput(String subject, String body) {
        // Synthetic message: blank sender/recipients/labels. Body-derived flags
        // are inferred from coarse markers so user-authored rules around
        // newsletters/unsubscribe links still have a chance to fire — matchers
        // that depend on real Gmail metadata (categories, labels, attachments)
        // intentionally fall through to NOT_MATCHED.
        Instant now = Instant.now(clock);
        String safeBody = body == null ? "" : body;
        boolean unsubscribeHinted = bodyContainsUnsubscribeMarker(safeBody);
        return new RuleEvaluationInput(
                "",
                "",
                List.of(),
                List.of(),
                subject == null ? "" : subject,
                List.of(),
                List.of(),
                now,
                now,
                false,
                unsubscribeHinted,
                unsubscribeHinted,
                Optional.of(!safeBody.isBlank()),
                Set.of());
    }

    private static boolean bodyContainsUnsubscribeMarker(String body) {
        return body.toLowerCase(Locale.ROOT).contains("unsubscribe");
    }

    private static RulePreviewResult.ActionChip toCustomActionChip(
            UUID ruleId, ActionIntent actionIntent, List<String> matchedEvidenceIds) {
        return new RulePreviewResult.ActionChip(
                customActionTypeId(actionIntent),
                safeActionLabel(actionIntent),
                List.of(ruleId),
                matchedEvidenceIds);
    }

    private static String customActionTypeId(ActionIntent actionIntent) {
        return switch (actionIntent) {
            case ActionIntent.Label _ -> "label";
            case ActionIntent.Archive _ -> "archive";
            case ActionIntent.SaveDraft _ -> "save_draft";
            case ActionIntent.MarkRead _ -> "mark_read";
            case ActionIntent.Star _ -> "star";
            case ActionIntent.AddToDigest _ -> "add_to_digest";
            case ActionIntent.MarkSpam _ -> "mark_spam";
            case ActionIntent.SendReply _ -> "send_reply";
            case ActionIntent.ForwardEmail _ -> "forward_email";
            case ActionIntent.SendEmail _ -> "send_email";
        };
    }

    private static List<RulePreviewResult.EvidenceChip> toEvidenceChipList(
            List<String> evidenceIds, java.util.Map<String, String> evidenceById) {
        return evidenceIds.stream()
                .map(
                        evidenceId ->
                                new RulePreviewResult.EvidenceChip(
                                        evidenceId,
                                        Objects.requireNonNullElse(
                                                evidenceById.get(evidenceId), "")))
                .toList();
    }

    // No @Transactional here on purpose: preview(...) is only invoked via
    // self-invocation from previewSavedRule / previewDraft, so Spring's
    // transactional proxy is bypassed and the annotation has no runtime
    // effect. The active transaction is owned by the public entry points.
    public RulePreviewResult preview(RulePreviewCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        PreviewSampleSize sampleSize = PreviewSampleSize.normalize(command.requestedSampleSize());
        PreviewTarget previewTarget =
                command.savedRulePreview()
                        ? savedPreviewTarget(command)
                        : draftPreviewTarget(command);
        boolean requiresBodyEvidence =
                previewTarget.candidates().stream()
                        .anyMatch(
                                previewCandidate ->
                                        previewCandidate.matcherNode().requiresBodyEvidence());
        List<RulePreviewDataService.PreviewInput> previewInputs =
                rulePreviewDataService.fetchPreviewInputs(
                        command.tenantId(), requiresBodyEvidence, sampleSize);
        Map<String, Map<String, Boolean>> semanticOverridesByMessage =
                command.evaluateSemanticIntents()
                        ? resolveSemanticOverrides(previewTarget.candidates(), previewInputs)
                        : Map.of();
        RulePreviewResult result =
                buildResult(
                        sampleSize,
                        previewTarget.candidates(),
                        previewInputs,
                        false,
                        semanticOverridesByMessage);
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

    private Map<String, Map<String, Boolean>> resolveSemanticOverrides(
            List<PreviewCandidate> candidates,
            List<RulePreviewDataService.PreviewInput> previewInputs) {
        if (llmGateway == null) {
            // Caller asked for semantic eval but no LLM gateway is wired in this
            // build (test profile, lite profile). Fall through to deferred chips
            // rather than fail loudly so the structural preview still surfaces.
            return Map.of();
        }
        LinkedHashMap<String, SemanticIntentMatcher> semanticIntentsByNodeId =
                new LinkedHashMap<>();
        for (PreviewCandidate candidate : candidates) {
            collectSemanticIntentMatchers(candidate.matcherNode(), semanticIntentsByNodeId);
        }
        if (semanticIntentsByNodeId.isEmpty()) {
            return Map.of();
        }
        List<SemanticIntentRequest> intents =
                semanticIntentsByNodeId.entrySet().stream()
                        .map(
                                entry ->
                                        new SemanticIntentRequest(
                                                entry.getKey(), entry.getValue().intent()))
                        .toList();
        LinkedHashMap<String, Map<String, Boolean>> overridesByMessage = new LinkedHashMap<>();
        for (RulePreviewDataService.PreviewInput previewInput : previewInputs) {
            String semanticContent =
                    buildSemanticEvaluationContent(previewInput.ruleEvaluationInput());
            Map<String, Boolean> overrides =
                    llmGateway.evaluateSemanticIntents(CallSite.PREVIEW, semanticContent, intents);
            overridesByMessage.put(previewInput.gmailMessageId(), overrides);
        }
        return overridesByMessage;
    }

    private static void collectSemanticIntentMatchers(
            MatcherNode matcherNode, Map<String, SemanticIntentMatcher> accumulator) {
        switch (matcherNode) {
            case SemanticIntentMatcher semanticIntentMatcher ->
                    accumulator.putIfAbsent(semanticIntentMatcher.nodeId(), semanticIntentMatcher);
            case MatcherNode.AllMatcher allMatcher -> {
                for (MatcherNode child : allMatcher.children()) {
                    collectSemanticIntentMatchers(child, accumulator);
                }
            }
            case MatcherNode.AnyMatcher anyMatcher -> {
                for (MatcherNode child : anyMatcher.children()) {
                    collectSemanticIntentMatchers(child, accumulator);
                }
            }
            case MatcherNode.NotMatcher notMatcher ->
                    collectSemanticIntentMatchers(notMatcher.child(), accumulator);
            default -> {
                // structural leaf — no semantic node to collect
            }
        }
    }

    private static String buildSemanticEvaluationContent(RuleEvaluationInput evaluationInput) {
        // Privacy + token-budget: preview path never has the raw body, so we
        // pass the sanitized subject plus a deterministic flag summary that
        // matches what the triage path builds (LlmGateway javadoc).
        StringBuilder content = new StringBuilder();
        content.append("subject: ").append(evaluationInput.sanitizedSubjectExcerpt());
        content.append("\nsender_domain: ").append(evaluationInput.sanitizedSenderDomain());
        if (evaluationInput.listUnsubscribePresent()) {
            content.append("\nflag: list_unsubscribe_present");
        }
        if (evaluationInput.newsletterIndicatorPresent()) {
            content.append("\nflag: newsletter_indicator");
        }
        if (evaluationInput.hasAttachment()) {
            content.append("\nflag: has_attachment");
        }
        return content.toString();
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
            boolean savedRuleMarkedPreviewed,
            Map<String, Map<String, Boolean>> semanticOverridesByMessage) {
        ArrayList<RulePreviewResult.PreviewRow> rows = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCounts = new LinkedHashMap<>();
        int matchedCount = 0;
        int deferredCount = 0;
        int conflictCount = 0;

        for (RulePreviewDataService.PreviewInput previewInput : previewInputs) {
            Map<String, Boolean> overridesForMessage =
                    semanticOverridesByMessage.getOrDefault(
                            previewInput.gmailMessageId(), Map.of());
            RowEvaluation rowEvaluation =
                    evaluateRow(previewCandidates, previewInput, overridesForMessage);
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
            List<PreviewCandidate> previewCandidates,
            RulePreviewDataService.PreviewInput previewInput,
            Map<String, Boolean> semanticOverrides) {
        ArrayList<ActionProposal> orderedProposals = new ArrayList<>();
        LinkedHashMap<String, String> matchedEvidenceById = new LinkedHashMap<>();
        LinkedHashMap<String, String> deferredEvidenceById = new LinkedHashMap<>();

        List<PreviewCandidate> orderedCandidates =
                previewCandidates.stream()
                        .filter(
                                candidate ->
                                        candidate.enabled()
                                                || candidate.includeDisabledRuleForPreview())
                        .sorted(java.util.Comparator.comparingInt(PreviewCandidate::orderIndex))
                        .toList();
        for (PreviewCandidate previewCandidate : orderedCandidates) {
            var evaluationResult =
                    ruleEvaluator.evaluate(
                            previewCandidate.matcherNode(),
                            previewInput.ruleEvaluationInput(),
                            semanticOverrides);
            if (evaluationResult.status() == MatcherEvaluationState.MATCHED) {
                for (String matchedEvidenceId : evaluationResult.matchedEvidenceIds()) {
                    matchedEvidenceById.put(
                            matchedEvidenceId,
                            evaluationResult.evidenceById().get(matchedEvidenceId));
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
                            deferredEvidenceId,
                            evaluationResult.evidenceById().get(deferredEvidenceId));
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
            case ActionIntent.Archive _ -> "archive";
            case ActionIntent.SaveDraft _ -> "save_draft";
            case ActionIntent.MarkRead _ -> "mark_read";
            case ActionIntent.Star _ -> "star";
            case ActionIntent.AddToDigest _ -> "add_to_digest";
            case ActionIntent.MarkSpam _ -> "mark_spam";
            case ActionIntent.SendReply _ -> "send_reply";
            case ActionIntent.ForwardEmail forwardEmail ->
                    "forward_email:" + String.join(",", forwardEmail.recipients());
            case ActionIntent.SendEmail sendEmail ->
                    "send_email:" + String.join(",", sendEmail.to());
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
            case SENDER_EMAIL ->
                    new MatcherNode.SenderEmailMatcher(nodeId, text(matcherNode, "email"));
            case SENDER_DOMAIN ->
                    new MatcherNode.SenderDomainMatcher(nodeId, text(matcherNode, "domain"));
            case RECIPIENT_TO ->
                    new MatcherNode.RecipientToMatcher(nodeId, text(matcherNode, "email"));
            case RECIPIENT_CC ->
                    new MatcherNode.RecipientCcMatcher(nodeId, text(matcherNode, "email"));
            case SUBJECT_CONTAINS ->
                    new MatcherNode.SubjectContainsMatcher(
                            nodeId, text(matcherNode, "text", "value"));
            case SUBJECT_EQUALS ->
                    new MatcherNode.SubjectEqualsMatcher(
                            nodeId, text(matcherNode, "text", "value"));
            case SUBJECT_REGEX ->
                    new MatcherNode.SubjectRegexMatcher(
                            nodeId, text(matcherNode, "regexPattern", "pattern"));
            case GMAIL_LABEL_PRESENT ->
                    new MatcherNode.GmailLabelPresentMatcher(nodeId, text(matcherNode, "labelId"));
            case GMAIL_LABEL_ABSENT ->
                    new MatcherNode.GmailLabelAbsentMatcher(nodeId, text(matcherNode, "labelId"));
            case GMAIL_CATEGORY_PRESENT ->
                    new MatcherNode.GmailCategoryPresentMatcher(
                            nodeId, text(matcherNode, "category"));
            case GMAIL_CATEGORY_ABSENT ->
                    new MatcherNode.GmailCategoryAbsentMatcher(
                            nodeId, text(matcherNode, "category"));
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
                            nodeId,
                            parseMatcherNode(required(matcherNode, "child"), nodeId + "-child"));
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
                RuleActionType actionType =
                        RuleActionType.fromId(text(actionIntentNode, "type", "action"));
                actionIntents.add(
                        switch (actionType) {
                            case LABEL ->
                                    new ActionIntent.Label(
                                            text(actionIntentNode, "labelName", "label"));
                            case ARCHIVE -> new ActionIntent.Archive();
                            case SAVE_DRAFT ->
                                    new ActionIntent.SaveDraft(
                                            text(actionIntentNode, "instruction", "draftIntent"));
                            case MARK_READ -> new ActionIntent.MarkRead();
                            case STAR -> new ActionIntent.Star();
                            case ADD_TO_DIGEST -> new ActionIntent.AddToDigest();
                            case MARK_SPAM -> new ActionIntent.MarkSpam();
                            case SEND_REPLY ->
                                    new ActionIntent.SendReply(
                                            text(actionIntentNode, "instruction", "body"));
                            case FORWARD_EMAIL ->
                                    new ActionIntent.ForwardEmail(
                                            recipients(actionIntentNode, "recipients", "to"),
                                            optionalText(actionIntentNode, "instruction", null));
                            case SEND_EMAIL ->
                                    new ActionIntent.SendEmail(
                                            recipients(actionIntentNode, "to", "recipients"),
                                            optionalRecipients(actionIntentNode, "cc"),
                                            optionalRecipients(actionIntentNode, "bcc"),
                                            text(actionIntentNode, "subject"),
                                            text(actionIntentNode, "body"));
                        });
            }
            return List.copyOf(actionIntents);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException(
                    "actionIntentsJson must be valid JSON", jacksonException);
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

    private static String text(
            JsonNode jsonNode, String primaryFieldName, String fallbackFieldName) {
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

    private static List<String> recipients(
            JsonNode jsonNode, String primaryFieldName, String fallbackFieldName) {
        JsonNode recipientNode = jsonNode.path(primaryFieldName);
        if ((recipientNode.isMissingNode() || recipientNode.isNull())
                && !primaryFieldName.equals(fallbackFieldName)) {
            recipientNode = jsonNode.path(fallbackFieldName);
        }
        if (recipientNode.isMissingNode() || recipientNode.isNull()) {
            throw new IllegalArgumentException(primaryFieldName + " is required");
        }
        return recipientArray(recipientNode, primaryFieldName);
    }

    private static List<String> optionalRecipients(JsonNode jsonNode, String fieldName) {
        JsonNode recipientNode = jsonNode.path(fieldName);
        if (recipientNode.isMissingNode() || recipientNode.isNull()) {
            return List.of();
        }
        return recipientArray(recipientNode, fieldName);
    }

    private static List<String> recipientArray(JsonNode recipientNode, String fieldName) {
        if (recipientNode.isString()) {
            return List.of(recipientNode.asString());
        }
        if (!recipientNode.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be an array");
        }
        ArrayList<String> recipients = new ArrayList<>();
        for (JsonNode singleRecipientNode : recipientNode) {
            if (!singleRecipientNode.isString()) {
                throw new IllegalArgumentException(fieldName + " must contain strings");
            }
            recipients.add(singleRecipientNode.asString());
        }
        return List.copyOf(recipients);
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

    private record PreviewTarget(
            List<PreviewCandidate> candidates, Integer savedRuleEntityVersion) {

        private PreviewTarget {
            candidates =
                    List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
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
                    List.copyOf(
                            Objects.requireNonNull(
                                    actionIntents, "actionIntents must not be null"));
        }
    }

    private record RowEvaluation(
            List<RulePreviewResult.ActionChip> actionChips,
            List<RulePreviewResult.EvidenceChip> matchedEvidenceChips,
            List<RulePreviewResult.EvidenceChip> deferredEvidenceChips,
            List<RulePreviewResult.ConflictChip> conflictChips) {}
}
