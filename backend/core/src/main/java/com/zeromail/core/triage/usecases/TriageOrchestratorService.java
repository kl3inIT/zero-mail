package com.zeromail.core.triage.usecases;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.ReservationId;
import com.zeromail.core.billing.usecases.CreditLedger;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.llm.exception.LlmEvaluationFailedException;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.llm.exception.TokenBudgetExceededException;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.llm.usecases.SemanticIntentRequest;
import com.zeromail.core.rules.domain.ActionIntent;
import com.zeromail.core.rules.domain.ActionProposal;
import com.zeromail.core.rules.domain.ActionProposalMerger;
import com.zeromail.core.rules.domain.MatcherEvaluationState;
import com.zeromail.core.rules.domain.MatcherNode;
import com.zeromail.core.rules.domain.MatcherType;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.rules.domain.RuleEvaluationInput;
import com.zeromail.core.rules.domain.RuleEvaluationResult;
import com.zeromail.core.rules.domain.RuleEvaluator;
import com.zeromail.core.rules.domain.SemanticIntentMatcher;
import com.zeromail.core.rules.usecases.RuleAutomationSettingsService;
import com.zeromail.core.rules.usecases.RuleManagementService;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.usecases.TenantService;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.thread.usecases.ThreadReplyClassificationInput;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.domain.TriageSafetyPolicy;
import com.zeromail.core.triage.exception.TriageSafetyViolationException;
import com.zeromail.core.triage.usecases.TriageAuditSaga.GmailWriteResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.ReservePhaseResult;
import com.zeromail.core.triage.usecases.TriageAuditSaga.TriageAuditCommand;
import com.zeromail.core.triage.usecases.TriageRuleEvaluationInputFactory.TriageRuleEvaluationInput;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Event-driven triage hero flow for observed Gmail messages.
 *
 * <p>v1 semantic matching deliberately sends only {@code semanticEvalContent}: the sanitized
 * subject excerpt plus a deterministic content-free summary of sender domain, labels, categories,
 * and flags. It never sends a body, snippet, raw headers, sender display name, prompt, completion,
 * or drafted reply content.
 */
@Service
public class TriageOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TriageOrchestratorService.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final int REASON_MAX_LENGTH = 512;
    private static final String AUTO_SEND_DISABLED = "AUTO_SEND_DISABLED";
    private static final String SENDER_SAFETY_NET = "SENDER_SAFETY_NET";
    private static final String LOW_TRUST_STATIC_FROM = "LOW_TRUST_STATIC_FROM";
    private static final String TENANT_CONTEXT_MISMATCH = "TENANT_CONTEXT_MISMATCH";
    private static final String OUTBOUND_SEND_FAILED = "OUTBOUND_SEND_FAILED";

    private final TenantService tenantService;
    private final TriageRuleEvaluationInputFactory triageRuleEvaluationInputFactory;
    private final RuleManagementService ruleManagementService;
    private final RuleAutomationSettingsService ruleAutomationSettingsService;
    private final LlmGateway llmGateway;
    private final CreditLedger creditLedger;
    private final ActionProposalMerger actionProposalMerger;
    private final RuleEvaluator ruleEvaluator;
    private final TriageSafetyPolicy triageSafetyPolicy;
    private final SenderSafetyNetService senderSafetyNetService;
    private final TriageAuditSaga triageAuditSaga;
    private final TriageDraftBodyGenerator draftBodyGenerator;
    private final TriageDraftSettings triageDraftSettings;
    private final ClassifyThreadReplyStatusService classifyThreadReplyStatusService;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate tenantScopedOrchestrationTransaction;

    public TriageOrchestratorService(
            TenantService tenantService,
            TriageRuleEvaluationInputFactory triageRuleEvaluationInputFactory,
            RuleManagementService ruleManagementService,
            RuleAutomationSettingsService ruleAutomationSettingsService,
            LlmGateway llmGateway,
            CreditLedger creditLedger,
            SenderSafetyNetService senderSafetyNetService,
            TriageAuditSaga triageAuditSaga,
            TriageDraftBodyGenerator draftBodyGenerator,
            TriageDraftSettings triageDraftSettings,
            ClassifyThreadReplyStatusService classifyThreadReplyStatusService,
            PlatformTransactionManager transactionManager,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.tenantService = tenantService;
        this.triageRuleEvaluationInputFactory = triageRuleEvaluationInputFactory;
        this.ruleManagementService = ruleManagementService;
        this.ruleAutomationSettingsService = ruleAutomationSettingsService;
        this.llmGateway = llmGateway;
        this.creditLedger = creditLedger;
        this.actionProposalMerger = new ActionProposalMerger();
        this.ruleEvaluator = new RuleEvaluator();
        this.triageSafetyPolicy = new TriageSafetyPolicy();
        this.senderSafetyNetService = senderSafetyNetService;
        this.triageAuditSaga = triageAuditSaga;
        this.draftBodyGenerator = draftBodyGenerator;
        this.triageDraftSettings = triageDraftSettings;
        this.classifyThreadReplyStatusService = classifyThreadReplyStatusService;
        this.meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.tenantScopedOrchestrationTransaction = new TransactionTemplate(transactionManager);
        this.tenantScopedOrchestrationTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @ApplicationModuleListener
    public void onMailMessageObserved(MailMessageObserved observedEvent) {
        TenantContext.runWith(
                observedEvent.tenantId(),
                () ->
                        tenantScopedOrchestrationTransaction.executeWithoutResult(
                                _ -> processObservedEvent(observedEvent)));
    }

    public OrchestrationResult processObservedEvent(MailMessageObserved observedEvent) {
        Objects.requireNonNull(observedEvent, "observedEvent must not be null");
        UUID tenantId = observedEvent.tenantId();
        TenantService.TenantTriageSettings triageSettings =
                tenantService.triageSettingsFor(tenantId);
        if (triageSettings.paused()) {
            log.info(
                    "event=triage_orchestration_paused tenantId={} gmailMessageId={}",
                    tenantId,
                    observedEvent.gmailMessageId());
            return OrchestrationResult.empty();
        }

        Optional<TriageRuleEvaluationInput> triageInput =
                triageRuleEvaluationInputFactory.fetch(observedEvent);
        if (triageInput.isEmpty()) {
            return OrchestrationResult.empty();
        }

        TriageRuleEvaluationInput triageRuleEvaluationInput = triageInput.get();
        RuleEvaluationInput ruleEvaluationInput = triageRuleEvaluationInput.evaluationInput();
        List<RuleExecutionCandidate> ruleExecutionCandidates = loadEnabledCandidates(tenantId);
        if (ruleExecutionCandidates.isEmpty()) {
            return OrchestrationResult.empty();
        }
        boolean autoSendRulesEnabled =
                ruleAutomationSettingsService.readOrDefault(tenantId).autoSendRulesEnabled();
        Map<UUID, Boolean> senderAnchoredByRuleId = senderAnchoredByRuleId(ruleExecutionCandidates);

        // semanticEvalContent is a sanitized subject excerpt plus content-free metadata flags only.
        String semanticEvalContent = buildSemanticEvalContent(ruleEvaluationInput);
        Map<String, MatcherEvaluationState> semanticMatches =
                resolveSemanticMatches(ruleExecutionCandidates, semanticEvalContent);
        if (semanticMatches.isEmpty()) {
            reserveDeterministicMessageCredit(tenantId);
        }

        List<ActionProposal> orderedProposals =
                evaluateRules(ruleExecutionCandidates, ruleEvaluationInput, semanticMatches);
        ActionProposalMerger.ActionProposalMergeResult mergeResult =
                actionProposalMerger.merge(orderedProposals, ruleEvaluationInput);
        if (mergeResult.proposals().isEmpty()) {
            return OrchestrationResult.empty();
        }

        SafetyNetEvaluation safetyNetEvaluation =
                safetyNetEvaluation(
                        tenantId, triageRuleEvaluationInput.sanitizedSenderEmail(), observedEvent);
        TriageDispatchContext dispatchContext =
                new TriageDispatchContext(
                        tenantId,
                        observedEvent.gmailMessageId(),
                        triageRuleEvaluationInput,
                        safetyNetEvaluation.blocksOutboundSend(),
                        safetyNetEvaluation.matchedPattern(),
                        autoSendRulesEnabled,
                        senderAnchoredByRuleId);
        int appliedActions = handleProposals(dispatchContext, mergeResult.proposals());
        return new OrchestrationResult(appliedActions);
    }

    /**
     * Per-message dispatch context. Bundles every value that is constant across the proposals loop
     * so {@link #handleProposals} and its helpers don't pass 7-parameter signatures around. {@code
     * triageRuleEvaluationInput} already carries {@code gmailThreadId} — accessors expose it so
     * call sites do not have to dereference twice.
     */
    private record TriageDispatchContext(
            UUID tenantId,
            String gmailMessageId,
            TriageRuleEvaluationInput triageRuleEvaluationInput,
            boolean blockedBySafetyNet,
            String blockedBySafetyNetPattern,
            boolean autoSendRulesEnabled,
            Map<UUID, Boolean> senderAnchoredByRuleId) {

        private TriageDispatchContext {
            senderAnchoredByRuleId = Map.copyOf(senderAnchoredByRuleId);
        }

        String gmailThreadId() {
            return triageRuleEvaluationInput.gmailThreadId();
        }
    }

    private List<RuleExecutionCandidate> loadEnabledCandidates(UUID tenantId) {
        return ruleManagementService.listEnabledForExecution(tenantId).stream()
                .map(
                        snapshot ->
                                new RuleExecutionCandidate(
                                        snapshot.id(),
                                        snapshot.displayName(),
                                        snapshot.orderIndex(),
                                        parseMatcher(snapshot.matcherAstJson()),
                                        parseActionIntents(snapshot.actionIntentsJson())))
                .toList();
    }

    private List<ActionProposal> evaluateRules(
            List<RuleExecutionCandidate> ruleExecutionCandidates,
            RuleEvaluationInput ruleEvaluationInput,
            Map<String, MatcherEvaluationState> semanticMatches) {
        ArrayList<ActionProposal> orderedProposals = new ArrayList<>();
        for (RuleExecutionCandidate ruleExecutionCandidate : ruleExecutionCandidates) {
            RuleEvaluationResult evaluationResult =
                    evaluateResolvedMatcher(
                            ruleExecutionCandidate.matcherNode(),
                            ruleEvaluationInput,
                            semanticMatches);
            if (evaluationResult.status() != MatcherEvaluationState.MATCHED) {
                continue;
            }
            for (ActionIntent actionIntent : ruleExecutionCandidate.actionIntents()) {
                orderedProposals.add(
                        new ActionProposal(
                                actionIntent,
                                List.of(ruleExecutionCandidate.ruleId()),
                                List.of(ruleExecutionCandidate.ruleName()),
                                evaluationResult.matchedEvidenceIds()));
            }
        }
        return List.copyOf(orderedProposals);
    }

    private int handleProposals(
            TriageDispatchContext dispatchContext, List<ActionProposal> actionProposals) {
        int appliedActions = 0;
        for (ActionProposal actionProposal : actionProposals) {
            RuleActionType actionType;
            try {
                actionType = triageSafetyPolicy.gate(actionProposal);
            } catch (TriageSafetyViolationException triageSafetyViolationException) {
                triageAuditSaga.recordTerminal(
                        commandFor(dispatchContext, actionProposal, actionProposal.type(), false),
                        TriageDecision.REJECTED_BY_SAFETY_POLICY);
                continue;
            }

            boolean outboundAction = isOutboundAction(actionType);
            String outboundFallbackReason =
                    outboundAction ? outboundFallbackReason(dispatchContext, actionProposal) : null;
            if (shouldSkipDraftWrite(dispatchContext, actionType, outboundFallbackReason)) {
                continue;
            }

            TriageAuditCommand command =
                    commandFor(
                            dispatchContext,
                            actionProposal,
                            actionType,
                            true,
                            outboundFallbackReason);
            String leaseOwner = "triage-orchestrator-" + UUID.randomUUID();
            ReservePhaseResult reservePhaseResult =
                    triageAuditSaga.reservePhase(command, leaseOwner);
            if (!reservePhaseResult.shouldAttemptGmail()
                    || reservePhaseResult.auditId().isEmpty()) {
                continue;
            }
            UUID auditId = reservePhaseResult.auditId().orElseThrow();
            try {
                GmailWriteResult gmailWriteResult =
                        executeGmailPhase(command, auditId, outboundAction, outboundFallbackReason);
                triageAuditSaga.finalizePhase(
                        dispatchContext.tenantId(), auditId, gmailWriteResult);
                if (gmailWriteResult.applied()
                        && command.preWriteIntent() instanceof TriageActionResult.SaveDraft) {
                    classifyAfterDraftSaved(dispatchContext, gmailWriteResult.externalRef());
                }
                appliedActions++;
            } catch (IOException | RuntimeException gmailWriteFailure) {
                triageAuditSaga.finalizePhase(
                        dispatchContext.tenantId(),
                        auditId,
                        GmailWriteResult.failed(gmailWriteFailure.getClass().getSimpleName()));
            }
        }
        return appliedActions;
    }

    private boolean shouldSkipDraftWrite(
            TriageDispatchContext dispatchContext,
            RuleActionType actionType,
            String outboundFallbackReason) {
        if (!wouldWriteDraft(actionType, outboundFallbackReason)) {
            return false;
        }
        if (triageDraftSettings.autoDraftRepliesEnabled(dispatchContext.tenantId())) {
            return false;
        }
        log.info(
                "event=draft.skipped reason=auto_disabled tenantId={} gmailMessageId={}",
                dispatchContext.tenantId(),
                dispatchContext.gmailMessageId());
        return true;
    }

    private static boolean wouldWriteDraft(
            RuleActionType actionType, String outboundFallbackReason) {
        return actionType == RuleActionType.SAVE_DRAFT || outboundFallbackReason != null;
    }

    private GmailWriteResult executeGmailPhase(
            TriageAuditCommand command,
            UUID auditId,
            boolean outboundAction,
            String outboundFallbackReason)
            throws IOException {
        if (!outboundAction) {
            return triageAuditSaga.gmailWritePhase(command);
        }
        if (TENANT_CONTEXT_MISMATCH.equals(outboundFallbackReason)) {
            return GmailWriteResult.failed(TENANT_CONTEXT_MISMATCH);
        }
        if (outboundFallbackReason != null) {
            return triageAuditSaga.outboundDraftFallbackPhase(
                    command, auditId, outboundFallbackReason);
        }
        try {
            return triageAuditSaga.outboundSendPhase(command, auditId);
        } catch (IOException | RuntimeException outboundSendFailure) {
            log.warn(
                    "event=triage_outbound_send_fallback tenantId={} gmailMessageId={} actionType={} failureClass={}",
                    command.tenantId(),
                    command.gmailMessageId(),
                    command.actionType(),
                    outboundSendFailure.getClass().getSimpleName());
            if (!triageDraftSettings.autoDraftRepliesEnabled(command.tenantId())) {
                log.info(
                        "event=draft.skipped reason=auto_disabled tenantId={} gmailMessageId={}",
                        command.tenantId(),
                        command.gmailMessageId());
                return GmailWriteResult.failed("AUTO_DRAFT_DISABLED");
            }
            return triageAuditSaga.outboundDraftFallbackPhase(
                    command, auditId, OUTBOUND_SEND_FAILED);
        }
    }

    private TriageAuditCommand commandFor(
            TriageDispatchContext dispatchContext,
            ActionProposal actionProposal,
            RuleActionType actionType,
            boolean generateDraftBody) {
        return commandFor(dispatchContext, actionProposal, actionType, generateDraftBody, null);
    }

    private TriageAuditCommand commandFor(
            TriageDispatchContext dispatchContext,
            ActionProposal actionProposal,
            RuleActionType actionType,
            boolean generateDraftBody,
            String fallbackReason) {
        TriageActionResult preWriteIntent =
                preWriteIntent(dispatchContext, actionProposal.actionIntent(), generateDraftBody);
        TriageRuleEvaluationInput evaluationContext = dispatchContext.triageRuleEvaluationInput();
        return new TriageAuditCommand(
                dispatchContext.tenantId(),
                dispatchContext.gmailMessageId(),
                dispatchContext.gmailThreadId(),
                evaluationContext.evaluationInput().sanitizedSubjectExcerpt(),
                evaluationContext.sanitizedSenderEmail(),
                firstContributingRuleId(actionProposal),
                firstContributingRuleName(actionProposal),
                actionType,
                preWriteIntent,
                replyHeadersFor(preWriteIntent, evaluationContext),
                reasonEvidence(actionProposal, fallbackReason),
                SENDER_SAFETY_NET.equals(fallbackReason)
                        ? dispatchContext.blockedBySafetyNetPattern()
                        : null);
    }

    private TriageActionResult preWriteIntent(
            TriageDispatchContext dispatchContext,
            ActionIntent actionIntent,
            boolean generateDraftBody) {
        return switch (actionIntent) {
            case ActionIntent.Label label ->
                    new TriageActionResult.Label(label.labelName(), label.labelName());
            case ActionIntent.Archive _ -> new TriageActionResult.Archive();
            case ActionIntent.SaveDraft saveDraft -> {
                TriageRuleEvaluationInput triageRuleEvaluationInput =
                        dispatchContext.triageRuleEvaluationInput();
                String draftBody =
                        generateDraftBody
                                ? draftBodyGenerator.generate(
                                        dispatchContext.tenantId(),
                                        dispatchContext.gmailThreadId(),
                                        draftGenerationSource(triageRuleEvaluationInput),
                                        triageRuleEvaluationInput
                                                .evaluationInput()
                                                .sanitizedSubjectExcerpt())
                                : saveDraft.instruction();
                yield new TriageActionResult.SaveDraft(
                        draftBody, null, dispatchContext.gmailThreadId());
            }
            case ActionIntent.MarkRead _ -> new TriageActionResult.MarkRead();
            case ActionIntent.Star _ -> new TriageActionResult.Star();
            case ActionIntent.AddToDigest _ -> new TriageActionResult.AddToDigest();
            case ActionIntent.MarkSpam _ -> new TriageActionResult.MarkSpam();
            case ActionIntent.SendReply sendReply -> {
                TriageRuleEvaluationInput triageRuleEvaluationInput =
                        dispatchContext.triageRuleEvaluationInput();
                String draftBody =
                        generateDraftBody
                                ? draftBodyGenerator.generate(
                                        dispatchContext.tenantId(),
                                        dispatchContext.gmailThreadId(),
                                        draftGenerationSource(triageRuleEvaluationInput)
                                                + "\nactionInstruction="
                                                + sendReply.instruction(),
                                        triageRuleEvaluationInput
                                                .evaluationInput()
                                                .sanitizedSubjectExcerpt())
                                : sendReply.instruction();
                yield new TriageActionResult.SendReply(
                        draftBody,
                        dispatchContext.gmailMessageId(),
                        dispatchContext.gmailThreadId());
            }
            case ActionIntent.ForwardEmail forwardEmail -> {
                String draftBody =
                        forwardEmail.instruction() == null
                                ? "Forwarding this email for review."
                                : forwardEmail.instruction();
                yield new TriageActionResult.ForwardEmail(forwardEmail.recipients(), draftBody);
            }
            case ActionIntent.SendEmail sendEmail ->
                    new TriageActionResult.SendEmail(
                            sendEmail.to(),
                            sendEmail.cc(),
                            sendEmail.bcc(),
                            sendEmail.subject(),
                            sendEmail.draftBody());
        };
    }

    private String draftGenerationSource(TriageRuleEvaluationInput triageRuleEvaluationInput) {
        return buildSemanticEvalContent(triageRuleEvaluationInput.evaluationInput());
    }

    private ReplyHeaders replyHeadersFor(
            TriageActionResult preWriteIntent,
            TriageRuleEvaluationInput triageRuleEvaluationInput) {
        if (!(preWriteIntent instanceof TriageActionResult.SaveDraft
                || preWriteIntent instanceof TriageActionResult.SendReply)) {
            return null;
        }
        return ReplyHeaders.of(
                triageRuleEvaluationInput.rfcMessageId(),
                triageRuleEvaluationInput.references(),
                triageRuleEvaluationInput.evaluationInput().sanitizedSubjectExcerpt(),
                triageRuleEvaluationInput.replyToAddress(),
                triageRuleEvaluationInput.gmailThreadId());
    }

    private void classifyAfterDraftSaved(TriageDispatchContext dispatchContext, String draftId) {
        classifyThreadReplyStatusService.classify(
                new ThreadReplyClassificationInput(
                        dispatchContext.tenantId(),
                        dispatchContext.gmailThreadId(),
                        dispatchContext.gmailMessageId(),
                        false,
                        dispatchContext
                                .triageRuleEvaluationInput()
                                .evaluationInput()
                                .gmailLabelIds()
                                .contains("SENT"),
                        true,
                        draftId,
                        false));
    }

    private SafetyNetEvaluation safetyNetEvaluation(
            UUID tenantId, String sanitizedSenderEmail, MailMessageObserved observedEvent) {
        if (sanitizedSenderEmail == null || sanitizedSenderEmail.isBlank()) {
            log.warn(
                    "event=triage_sender_missing tenantId={} gmailMessageId={}",
                    tenantId,
                    observedEvent.gmailMessageId());
            return SafetyNetEvaluation.blockedWithoutPattern();
        }
        try {
            return senderSafetyNetService
                    .matchedProtectedPattern(tenantId, sanitizedSenderEmail)
                    .map(SafetyNetEvaluation::blockedByPattern)
                    .orElseGet(SafetyNetEvaluation::notBlocked);
        } catch (IllegalArgumentException senderSafetyNetFailure) {
            log.warn(
                    "event=triage_sender_malformed tenantId={} gmailMessageId={}",
                    tenantId,
                    observedEvent.gmailMessageId());
            return SafetyNetEvaluation.blockedWithoutPattern();
        }
    }

    private record SafetyNetEvaluation(boolean blocksOutboundSend, String matchedPattern) {

        static SafetyNetEvaluation notBlocked() {
            return new SafetyNetEvaluation(false, null);
        }

        static SafetyNetEvaluation blockedByPattern(String matchedPattern) {
            return new SafetyNetEvaluation(true, matchedPattern);
        }

        static SafetyNetEvaluation blockedWithoutPattern() {
            return new SafetyNetEvaluation(true, null);
        }
    }

    private Map<String, MatcherEvaluationState> resolveSemanticMatches(
            List<RuleExecutionCandidate> ruleExecutionCandidates, String semanticEvalContent) {
        List<RuleSemanticRequests> semanticRequestsByRule =
                ruleExecutionCandidates.stream()
                        .map(
                                ruleExecutionCandidate ->
                                        new RuleSemanticRequests(
                                                ruleExecutionCandidate,
                                                semanticRequests(
                                                        ruleExecutionCandidate.matcherNode())))
                        .filter(
                                ruleSemanticRequests ->
                                        !ruleSemanticRequests.semanticRequests().isEmpty())
                        .toList();
        if (semanticRequestsByRule.isEmpty()) {
            return Map.of();
        }

        List<SemanticIntentRequest> allSemanticRequests =
                semanticRequestsByRule.stream()
                        .flatMap(
                                ruleSemanticRequests ->
                                        ruleSemanticRequests.semanticRequests().stream())
                        .toList();
        try {
            return semanticEvaluationStates(
                    llmGateway.evaluateSemanticIntents(
                            CallSite.TRIAGE_PLATFORM_LLM,
                            semanticEvalContent,
                            allSemanticRequests));
        } catch (TokenBudgetExceededException tokenBudgetExceededException) {
            return resolveSemanticMatchesPerRule(semanticRequestsByRule, semanticEvalContent);
        } catch (LlmEvaluationFailedException | SafetyViolationException semanticFailure) {
            return failedSemanticMatches(
                    allSemanticRequests, semanticFailure.getClass().getSimpleName());
        }
    }

    private Map<String, MatcherEvaluationState> resolveSemanticMatchesPerRule(
            List<RuleSemanticRequests> semanticRequestsByRule, String semanticEvalContent) {
        LinkedHashMap<String, MatcherEvaluationState> semanticMatches = new LinkedHashMap<>();
        for (RuleSemanticRequests ruleSemanticRequests : semanticRequestsByRule) {
            meterRegistry.counter("triage.semantic_eval.fanout.per_rule").increment();
            try {
                semanticMatches.putAll(
                        semanticEvaluationStates(
                                llmGateway.evaluateSemanticIntents(
                                        CallSite.TRIAGE_PLATFORM_LLM,
                                        semanticEvalContent,
                                        ruleSemanticRequests.semanticRequests())));
            } catch (TokenBudgetExceededException
                    | LlmEvaluationFailedException
                    | SafetyViolationException semanticFailure) {
                semanticMatches.putAll(
                        failedSemanticMatches(
                                ruleSemanticRequests.semanticRequests(),
                                semanticFailure.getClass().getSimpleName()));
            }
        }
        return Map.copyOf(semanticMatches);
    }

    private Map<String, MatcherEvaluationState> semanticEvaluationStates(
            Map<String, Boolean> semanticMatches) {
        LinkedHashMap<String, MatcherEvaluationState> semanticEvaluationStates =
                new LinkedHashMap<>();
        semanticMatches.forEach(
                (nodeId, matched) ->
                        semanticEvaluationStates.put(
                                nodeId,
                                matched
                                        ? MatcherEvaluationState.MATCHED
                                        : MatcherEvaluationState.NOT_MATCHED));
        return Map.copyOf(semanticEvaluationStates);
    }

    private Map<String, MatcherEvaluationState> failedSemanticMatches(
            List<SemanticIntentRequest> semanticIntentRequests, String failureClassName) {
        LinkedHashMap<String, MatcherEvaluationState> semanticMatches = new LinkedHashMap<>();
        for (SemanticIntentRequest semanticIntentRequest : semanticIntentRequests) {
            semanticMatches.put(semanticIntentRequest.nodeId(), MatcherEvaluationState.DEFERRED);
            log.warn(
                    "event=triage_semantic_deferred_error nodeId={} failureClass={}",
                    semanticIntentRequest.nodeId(),
                    failureClassName);
        }
        return Map.copyOf(semanticMatches);
    }

    /**
     * Builds v1 {@code semanticEvalContent}: sanitized subject excerpt plus deterministic
     * content-free metadata flags only. Do not extend this with body, snippet, raw header, sender
     * display-name, prompt, completion, or drafted-reply content without a new privacy review.
     */
    private String buildSemanticEvalContent(RuleEvaluationInput ruleEvaluationInput) {
        // NOTE: do NOT add body/snippet/raw-header fetch here.
        return String.join(
                "\n",
                "subjectExcerpt=" + ruleEvaluationInput.sanitizedSubjectExcerpt(),
                "senderDomain=" + ruleEvaluationInput.sanitizedSenderDomain(),
                "labelCount=" + ruleEvaluationInput.gmailLabelIds().size(),
                "categories=" + String.join(",", ruleEvaluationInput.gmailCategories()),
                "hasAttachment=" + ruleEvaluationInput.hasAttachment(),
                "listUnsubscribePresent=" + ruleEvaluationInput.listUnsubscribePresent(),
                "newsletterIndicatorPresent=" + ruleEvaluationInput.newsletterIndicatorPresent(),
                "internalDate=" + ruleEvaluationInput.internalDate());
    }

    private void reserveDeterministicMessageCredit(UUID tenantId) {
        if (CallSite.TRIAGE_DETERMINISTIC.cost() == 0) {
            return;
        }
        ReservationId reservationId = creditLedger.reserve(tenantId, CallSite.TRIAGE_DETERMINISTIC);
        boolean settled = false;
        try {
            creditLedger.settle(reservationId);
            settled = true;
        } finally {
            if (!settled) {
                creditLedger.release(reservationId);
            }
        }
    }

    private RuleEvaluationResult evaluateResolvedMatcher(
            MatcherNode matcherNode,
            RuleEvaluationInput ruleEvaluationInput,
            Map<String, MatcherEvaluationState> semanticMatches) {
        if (matcherNode instanceof SemanticIntentMatcher semanticIntentMatcher) {
            MatcherEvaluationState semanticState =
                    semanticMatches.getOrDefault(
                            semanticIntentMatcher.nodeId(), MatcherEvaluationState.NOT_MATCHED);
            return switch (semanticState) {
                case MATCHED ->
                        RuleEvaluationResult.matched(
                                semanticIntentMatcher.nodeId(), "semantic_intent_matched");
                case NOT_MATCHED ->
                        RuleEvaluationResult.notMatched(
                                semanticIntentMatcher.nodeId(), "semantic_intent_not_matched");
                case DEFERRED ->
                        RuleEvaluationResult.deferred(
                                semanticIntentMatcher.nodeId(), "semantic_intent_deferred");
            };
        }
        if (matcherNode instanceof MatcherNode.AllMatcher allMatcher) {
            return evaluateAll(allMatcher, ruleEvaluationInput, semanticMatches);
        }
        if (matcherNode instanceof MatcherNode.AnyMatcher anyMatcher) {
            return evaluateAny(anyMatcher, ruleEvaluationInput, semanticMatches);
        }
        if (matcherNode instanceof MatcherNode.NotMatcher notMatcher) {
            return evaluateNot(notMatcher, ruleEvaluationInput, semanticMatches);
        }
        return ruleEvaluator.evaluate(matcherNode, ruleEvaluationInput);
    }

    private RuleEvaluationResult evaluateAll(
            MatcherNode.AllMatcher allMatcher,
            RuleEvaluationInput ruleEvaluationInput,
            Map<String, MatcherEvaluationState> semanticMatches) {
        ArrayList<RuleEvaluationResult> childResults = new ArrayList<>();
        MatcherEvaluationState status = MatcherEvaluationState.MATCHED;
        for (MatcherNode childMatcherNode : allMatcher.children()) {
            RuleEvaluationResult childResult =
                    evaluateResolvedMatcher(childMatcherNode, ruleEvaluationInput, semanticMatches);
            childResults.add(childResult);
            if (childResult.status() == MatcherEvaluationState.NOT_MATCHED) {
                status = MatcherEvaluationState.NOT_MATCHED;
            } else if (status == MatcherEvaluationState.MATCHED
                    && childResult.status() == MatcherEvaluationState.DEFERRED) {
                status = MatcherEvaluationState.DEFERRED;
            }
        }
        return RuleEvaluationResult.compose(status, childResults);
    }

    private RuleEvaluationResult evaluateAny(
            MatcherNode.AnyMatcher anyMatcher,
            RuleEvaluationInput ruleEvaluationInput,
            Map<String, MatcherEvaluationState> semanticMatches) {
        ArrayList<RuleEvaluationResult> childResults = new ArrayList<>();
        boolean anyMatched = false;
        boolean anyDeferred = false;
        for (MatcherNode childMatcherNode : anyMatcher.children()) {
            RuleEvaluationResult childResult =
                    evaluateResolvedMatcher(childMatcherNode, ruleEvaluationInput, semanticMatches);
            childResults.add(childResult);
            anyMatched = anyMatched || childResult.status() == MatcherEvaluationState.MATCHED;
            anyDeferred = anyDeferred || childResult.status() == MatcherEvaluationState.DEFERRED;
        }
        MatcherEvaluationState status =
                anyMatched
                        ? MatcherEvaluationState.MATCHED
                        : anyDeferred
                                ? MatcherEvaluationState.DEFERRED
                                : MatcherEvaluationState.NOT_MATCHED;
        return RuleEvaluationResult.compose(status, childResults);
    }

    private RuleEvaluationResult evaluateNot(
            MatcherNode.NotMatcher notMatcher,
            RuleEvaluationInput ruleEvaluationInput,
            Map<String, MatcherEvaluationState> semanticMatches) {
        RuleEvaluationResult childResult =
                evaluateResolvedMatcher(notMatcher.child(), ruleEvaluationInput, semanticMatches);
        MatcherEvaluationState status =
                switch (childResult.status()) {
                    case MATCHED -> MatcherEvaluationState.NOT_MATCHED;
                    case NOT_MATCHED -> MatcherEvaluationState.MATCHED;
                    case DEFERRED -> MatcherEvaluationState.DEFERRED;
                };
        return RuleEvaluationResult.compose(status, List.of(childResult));
    }

    private List<SemanticIntentRequest> semanticRequests(MatcherNode matcherNode) {
        ArrayList<SemanticIntentRequest> semanticRequests = new ArrayList<>();
        collectSemanticRequests(matcherNode, semanticRequests);
        return List.copyOf(semanticRequests);
    }

    private void collectSemanticRequests(
            MatcherNode matcherNode, List<SemanticIntentRequest> semanticRequests) {
        if (matcherNode instanceof SemanticIntentMatcher semanticIntentMatcher) {
            semanticRequests.add(
                    new SemanticIntentRequest(
                            semanticIntentMatcher.nodeId(), semanticIntentMatcher.intent()));
            return;
        }
        switch (matcherNode) {
            case MatcherNode.AllMatcher allMatcher ->
                    allMatcher
                            .children()
                            .forEach(
                                    childMatcherNode ->
                                            collectSemanticRequests(
                                                    childMatcherNode, semanticRequests));
            case MatcherNode.AnyMatcher anyMatcher ->
                    anyMatcher
                            .children()
                            .forEach(
                                    childMatcherNode ->
                                            collectSemanticRequests(
                                                    childMatcherNode, semanticRequests));
            case MatcherNode.NotMatcher notMatcher ->
                    collectSemanticRequests(notMatcher.child(), semanticRequests);
            default -> {}
        }
    }

    private MatcherNode parseMatcher(String matcherJson) {
        try {
            return parseMatcherNode(OBJECT_MAPPER.readTree(matcherJson), "matcher");
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
                    new MatcherNode.GmailLabelPresentMatcher(
                            nodeId, text(matcherNode, "labelId", "label"));
            case GMAIL_LABEL_ABSENT ->
                    new MatcherNode.GmailLabelAbsentMatcher(
                            nodeId, text(matcherNode, "labelId", "label"));
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
            JsonNode rootNode = OBJECT_MAPPER.readTree(actionIntentsJson);
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

    private static UUID firstContributingRuleId(ActionProposal actionProposal) {
        return actionProposal.contributingRuleIds().getFirst();
    }

    private static String firstContributingRuleName(ActionProposal actionProposal) {
        return actionProposal.contributingRuleNames().getFirst();
    }

    private static String reasonEvidence(ActionProposal actionProposal, String fallbackReason) {
        String reason =
                "ruleIds="
                        + join(actionProposal.contributingRuleIds())
                        + ";evidenceIds="
                        + String.join(",", actionProposal.evidenceIds());
        if (fallbackReason != null && !fallbackReason.isBlank()) {
            reason = reason + ";outboundFallbackReason=" + fallbackReason;
        }
        return reason.length() <= REASON_MAX_LENGTH
                ? reason
                : reason.substring(0, REASON_MAX_LENGTH);
    }

    private static boolean isOutboundAction(RuleActionType actionType) {
        return switch (actionType) {
            case SEND_REPLY, FORWARD_EMAIL, SEND_EMAIL -> true;
            case LABEL, ARCHIVE, SAVE_DRAFT, MARK_READ, STAR, ADD_TO_DIGEST, MARK_SPAM -> false;
        };
    }

    private String outboundFallbackReason(
            TriageDispatchContext dispatchContext, ActionProposal actionProposal) {
        Optional<String> currentTenant = TenantContext.currentOptional();
        if (currentTenant.isEmpty()
                || !currentTenant.get().equals(dispatchContext.tenantId().toString())) {
            return TENANT_CONTEXT_MISMATCH;
        }
        if (!dispatchContext.autoSendRulesEnabled()) {
            return AUTO_SEND_DISABLED;
        }
        if (dispatchContext.blockedBySafetyNet()) {
            return SENDER_SAFETY_NET;
        }
        if (!allContributingRulesHaveSenderAnchor(dispatchContext, actionProposal)) {
            return LOW_TRUST_STATIC_FROM;
        }
        return null;
    }

    private static boolean allContributingRulesHaveSenderAnchor(
            TriageDispatchContext dispatchContext, ActionProposal actionProposal) {
        for (UUID ruleId : actionProposal.contributingRuleIds()) {
            if (!dispatchContext.senderAnchoredByRuleId().getOrDefault(ruleId, false)) {
                return false;
            }
        }
        return true;
    }

    private static Map<UUID, Boolean> senderAnchoredByRuleId(
            List<RuleExecutionCandidate> ruleExecutionCandidates) {
        LinkedHashMap<UUID, Boolean> senderAnchoredByRuleId = new LinkedHashMap<>();
        for (RuleExecutionCandidate ruleExecutionCandidate : ruleExecutionCandidates) {
            senderAnchoredByRuleId.put(
                    ruleExecutionCandidate.ruleId(),
                    matcherRequiresTrustedSender(ruleExecutionCandidate.matcherNode()));
        }
        return Map.copyOf(senderAnchoredByRuleId);
    }

    private static boolean matcherRequiresTrustedSender(MatcherNode matcherNode) {
        return switch (matcherNode) {
            case MatcherNode.SenderEmailMatcher _ -> true;
            case MatcherNode.SenderDomainMatcher _ -> true;
            case MatcherNode.AllMatcher allMatcher ->
                    allMatcher.children().stream()
                            .anyMatch(TriageOrchestratorService::matcherRequiresTrustedSender);
            case MatcherNode.AnyMatcher anyMatcher ->
                    anyMatcher.children().stream()
                            .allMatch(TriageOrchestratorService::matcherRequiresTrustedSender);
            case MatcherNode.NotMatcher _ -> false;
            default -> false;
        };
    }

    private static String join(List<UUID> values) {
        return values.stream()
                .map(UUID::toString)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private record RuleExecutionCandidate(
            UUID ruleId,
            String ruleName,
            int ruleOrder,
            MatcherNode matcherNode,
            List<ActionIntent> actionIntents) {

        private RuleExecutionCandidate {
            Objects.requireNonNull(ruleId, "ruleId must not be null");
            Objects.requireNonNull(ruleName, "ruleName must not be null");
            Objects.requireNonNull(matcherNode, "matcherNode must not be null");
            actionIntents =
                    List.copyOf(
                            Objects.requireNonNull(
                                    actionIntents, "actionIntents must not be null"));
        }
    }

    private record RuleSemanticRequests(
            RuleExecutionCandidate ruleExecutionCandidate,
            List<SemanticIntentRequest> semanticRequests) {

        private RuleSemanticRequests {
            Objects.requireNonNull(
                    ruleExecutionCandidate, "ruleExecutionCandidate must not be null");
            semanticRequests =
                    List.copyOf(
                            Objects.requireNonNull(
                                    semanticRequests, "semanticRequests must not be null"));
        }
    }

    public record OrchestrationResult(int appliedActions) {
        static OrchestrationResult empty() {
            return new OrchestrationResult(0);
        }
    }
}
