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
import com.zeromail.core.outbound.usecases.OutboundSendThrottle;
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
import com.zeromail.core.rules.domain.SemanticEvalContentBuilder;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.TransientDataAccessException;
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

    // Admission control for triage. Virtual threads (spring.threads.virtual.enabled=true) make the
    // @Async @ApplicationModuleListener concurrency effectively unbounded, so a TriageEventRetryJob
    // burst can resubmit dozens of events at once. Each event holds TWO worker DB connections (the
    // idle outer listener tx + the active inner REQUIRES_NEW tx), so unbounded concurrency starves
    // the Hikari pool (CannotCreateTransactionException). Cap concurrent triage at worker_pool / 2;
    // excess events park cheaply on virtual threads until a permit frees. Keep this ≤
    // ZERO_MAIL_WORKER_DB_POOL_MAX / 2 (pool 16 → 8).
    private static final int MAX_CONCURRENT_TRIAGE = 8;
    private final Semaphore triageConcurrencyLimiter = new Semaphore(MAX_CONCURRENT_TRIAGE);

    // Spring Framework 7 core resilience: retry only TRANSIENT data-access faults (a brief DB
    // connection blip / lock timeout) inline with exponential backoff + jitter, so a transient
    // hiccup recovers in milliseconds instead of failing the event and waiting for the
    // (minutes-scale) Modulith resubmission cycle. Systematic failures (e.g. a logic/IllegalState
    // bug) are NOT in `includes`, so they fail fast and fall through to Modulith — inline retry
    // must
    // never mask a real bug or block for a long outage (that's the resubmission job's job).
    private static final RetryPolicy TRANSIENT_TRIAGE_RETRY_POLICY =
            RetryPolicy.builder()
                    .includes(TransientDataAccessException.class)
                    .maxRetries(2)
                    .delay(Duration.ofMillis(200))
                    .multiplier(2.0)
                    .maxDelay(Duration.ofSeconds(2))
                    .jitter(Duration.ofMillis(50))
                    .build();
    private final RetryTemplate transientTriageRetry =
            new RetryTemplate(TRANSIENT_TRIAGE_RETRY_POLICY);
    private static final String AUTO_SEND_DISABLED = "AUTO_SEND_DISABLED";
    private static final String SENDER_SAFETY_NET = "SENDER_SAFETY_NET";
    private static final String TENANT_CONTEXT_MISMATCH = "TENANT_CONTEXT_MISMATCH";
    private static final String OUTBOUND_SEND_FAILED = "OUTBOUND_SEND_FAILED";
    private static final String OUTBOUND_RATE_LIMITED = "OUTBOUND_RATE_LIMITED";

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
    private final OutboundSendThrottle outboundSendThrottle;
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
            OutboundSendThrottle outboundSendThrottle,
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
        this.outboundSendThrottle = outboundSendThrottle;
        this.meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.tenantScopedOrchestrationTransaction = new TransactionTemplate(transactionManager);
        this.tenantScopedOrchestrationTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @ApplicationModuleListener
    public void onMailMessageObserved(MailMessageObserved observedEvent) {
        // The Hibernate @TenantId discriminator resolves the current tenant via
        // ScopedValueTenantResolver when the JPA session OPENS. The session must therefore open
        // AFTER TenantContext is set, or every tenant-owned query (e.g. gmail_connections) is
        // filtered by the BOOTSTRAP_TENANT sentinel and returns empty ("No connection for
        // tenantId"). Opening a REQUIRES_NEW transaction INSIDE TenantContext.runWith guarantees
        // the session — and thus the tenant resolution — happens with the tenant in scope.
        // (Removing this wrapper in commit 0675be1a broke multi-tenant triage entirely.)
        // Admission control: acquire a permit BEFORE opening any transaction so a resubmission
        // burst can never demand more than 2 * MAX_CONCURRENT_TRIAGE pool connections at once.
        try {
            triageConcurrencyLimiter.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while awaiting a triage concurrency permit", interrupted);
        }
        try {
            TenantContext.runWith(
                    observedEvent.tenantId(), () -> runTriageWithTransientRetry(observedEvent));
        } finally {
            triageConcurrencyLimiter.release();
        }
    }

    private void runTriageWithTransientRetry(MailMessageObserved observedEvent) {
        // RetryTemplate wraps the inner REQUIRES_NEW template so each transient retry opens a FRESH
        // transaction with the tenant already in scope (never reuses a rolled-back one).
        try {
            transientTriageRetry.execute(
                    () -> {
                        tenantScopedOrchestrationTransaction.executeWithoutResult(
                                _ -> processObservedEvent(observedEvent));
                        return null;
                    });
        } catch (RetryException transientRetriesExhausted) {
            // Transient retries exhausted — propagate so Modulith records the failure and the
            // backoff resubmission job (TriageEventRetryJob) drives longer-horizon recovery.
            Throwable cause = transientRetriesExhausted.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Triage failed after transient retries", cause);
        }
    }

    /**
     * Classification-only entry point for the needs-reply backfill: fetches the same metadata-only
     * triage input as the live path and runs reply-status classification (LLM needs-reply +
     * persist), but never evaluates rules and never writes to Gmail. Caller binds {@link
     * TenantContext}. Safe to re-run (the classifier is idempotent per message id).
     */
    public void classifyReplyStatusForBackfill(MailMessageObserved observedEvent) {
        Objects.requireNonNull(observedEvent, "observedEvent must not be null");
        triageRuleEvaluationInputFactory
                .fetch(observedEvent)
                .ifPresent(
                        triageRuleEvaluationInput ->
                                classifyInboundReplyStatus(
                                        observedEvent,
                                        triageRuleEvaluationInput,
                                        DispatchOutcome.none(),
                                        true));
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

        // Run the rules pipeline (which may early-exit when there are no rules / no matches), then
        // ALWAYS classify this inbound message's reply status. Reply-status tracking is a separate
        // concern from the rules engine: the "Cần trả lời" inbox must surface incoming mail even
        // when no rule matched, so classification cannot live behind the rules early-returns.
        DispatchOutcome dispatchOutcome =
                runRulesPipeline(tenantId, observedEvent, triageRuleEvaluationInput);
        classifyInboundReplyStatus(
                observedEvent, triageRuleEvaluationInput, dispatchOutcome, false);
        return new OrchestrationResult(dispatchOutcome.appliedActions());
    }

    private DispatchOutcome runRulesPipeline(
            UUID tenantId,
            MailMessageObserved observedEvent,
            TriageRuleEvaluationInput triageRuleEvaluationInput) {
        RuleEvaluationInput ruleEvaluationInput = triageRuleEvaluationInput.evaluationInput();
        // Loop-breaker (mail-loop guard): never run the inbound rules engine against a message the
        // tenant themselves authored. An auto-send action drops a copy into SENT; the
        // label-agnostic
        // history sweep (history.list has no INBOX filter) re-observes it, and an intent-only
        // matcher
        // (e.g. "reply to any invite") can re-match that SENT copy → another auto-send → unbounded
        // loop. The triage idempotency index keys on gmail_message_id, so each loop hop is a NEW
        // row
        // and never deduplicates. Rules are an INBOUND concern; the SENT half is handled separately
        // by MailOutboundObserved reply-status classification, which still runs after this
        // pipeline.
        // Self-addressed mail (INBOX + SENT) is intentionally skipped too — safety outranks the
        // rare
        // note-to-self rule.
        if (ruleEvaluationInput.gmailLabelIds() != null
                && ruleEvaluationInput.gmailLabelIds().contains("SENT")) {
            log.info(
                    "event=triage_rules_skipped_self_sent tenantId={} gmailMessageId={}",
                    tenantId,
                    observedEvent.gmailMessageId());
            return DispatchOutcome.none();
        }
        List<RuleExecutionCandidate> ruleExecutionCandidates = loadEnabledCandidates(tenantId);
        if (ruleExecutionCandidates.isEmpty()) {
            return DispatchOutcome.none();
        }
        boolean autoSendRulesEnabled =
                ruleAutomationSettingsService.readOrDefault(tenantId).autoSendRulesEnabled();

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
            return DispatchOutcome.none();
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
                        autoSendRulesEnabled);
        return handleProposals(dispatchContext, mergeResult.proposals());
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
            boolean autoSendRulesEnabled) {

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

    private DispatchOutcome handleProposals(
            TriageDispatchContext dispatchContext, List<ActionProposal> actionProposals) {
        int appliedActions = 0;
        boolean draftSaved = false;
        String savedDraftId = null;
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
            if (shouldSkipDraftWrite(dispatchContext, actionType)) {
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
                        && command.preWriteIntent() instanceof TriageActionResult.SaveDraft
                        && gmailWriteResult.externalRef() != null
                        && !gmailWriteResult.externalRef().isBlank()) {
                    // A reply draft was saved for this inbound message; the single
                    // classifyInboundReplyStatus call at the end of processObservedEvent records
                    // the
                    // draft and keeps the thread in TO_REPLY (no separate classify call here, so
                    // the
                    // observed-message id is classified exactly once).
                    draftSaved = true;
                    savedDraftId = gmailWriteResult.externalRef();
                }
                appliedActions++;
            } catch (IOException | RuntimeException gmailWriteFailure) {
                triageAuditSaga.finalizePhase(
                        dispatchContext.tenantId(),
                        auditId,
                        GmailWriteResult.failed(gmailWriteFailure.getClass().getSimpleName()));
            }
        }
        return new DispatchOutcome(appliedActions, draftSaved, savedDraftId);
    }

    private boolean shouldSkipDraftWrite(
            TriageDispatchContext dispatchContext, RuleActionType actionType) {
        // Only an explicit save_draft action writes a draft now — outbound actions never downgrade
        // to one — so the auto-draft toggle only gates save_draft.
        if (actionType != RuleActionType.SAVE_DRAFT) {
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

    private GmailWriteResult executeGmailPhase(
            TriageAuditCommand command,
            UUID auditId,
            boolean outboundAction,
            String outboundFallbackReason)
            throws IOException {
        if (!outboundAction) {
            return triageAuditSaga.gmailWritePhase(command);
        }
        // Outbound actions are NEVER downgraded to a Gmail draft (product decision). A blocked send
        // (auto-send off, protected sender, rate-limited, tenant-context mismatch) or a failed send
        // is recorded as a failed audit with its reason — no draft is written, so a send/forward
        // rule that cannot send simply does nothing rather than leaving a surprise draft.
        if (outboundFallbackReason != null) {
            return GmailWriteResult.failed(outboundFallbackReason);
        }
        try {
            return triageAuditSaga.outboundSendPhase(command, auditId);
        } catch (IOException | RuntimeException outboundSendFailure) {
            log.warn(
                    "event=triage_outbound_send_failed tenantId={} gmailMessageId={} actionType={} failureClass={}",
                    command.tenantId(),
                    command.gmailMessageId(),
                    command.actionType(),
                    outboundSendFailure.getClass().getSimpleName());
            return GmailWriteResult.failed(OUTBOUND_SEND_FAILED);
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

    /**
     * Runs once per observed inbound message (every {@link #processObservedEvent} call that fetched
     * a message), so the "Cần trả lời" inbox tracks all incoming mail — not only threads a rule
     * drafted a reply for. {@code MailMessageObserved} fires for INBOX messages, so the last
     * message is inbound ({@code lastMessageFromIsTenant=false}); the SENT half is classified by
     * {@link ClassifyThreadReplyStatusService}'s {@code MailOutboundObserved} listener.
     */
    private void classifyInboundReplyStatus(
            MailMessageObserved observedEvent,
            TriageRuleEvaluationInput triageRuleEvaluationInput,
            DispatchOutcome dispatchOutcome,
            boolean forceReclassify) {
        RuleEvaluationInput ruleEvaluationInput = triageRuleEvaluationInput.evaluationInput();
        boolean inboundReplyNeeded =
                resolveInboundReplyNeeded(
                        observedEvent.tenantId(), ruleEvaluationInput, dispatchOutcome);
        ThreadReplyClassificationInput classificationInput =
                new ThreadReplyClassificationInput(
                        observedEvent.tenantId(),
                        triageRuleEvaluationInput.gmailThreadId(),
                        observedEvent.gmailMessageId(),
                        false,
                        ruleEvaluationInput.gmailLabelIds().contains("SENT"),
                        dispatchOutcome.draftSaved(),
                        dispatchOutcome.draftId(),
                        false,
                        inboundReplyNeeded);
        if (forceReclassify) {
            classifyThreadReplyStatusService.reclassify(classificationInput);
        } else {
            classifyThreadReplyStatusService.classify(classificationInput);
        }
    }

    /**
     * Resolves the needs-reply-vs-FYI signal for an inbound message. A drafted reply implies a
     * reply is needed; obvious bulk/no-reply mail is FYI without an LLM call; everything else asks
     * the LLM. On any LLM failure we fail open to TO_REPLY so a real message is never silently
     * hidden in FYI.
     */
    private boolean resolveInboundReplyNeeded(
            UUID tenantId,
            RuleEvaluationInput ruleEvaluationInput,
            DispatchOutcome dispatchOutcome) {
        if (dispatchOutcome.draftSaved()) {
            return true;
        }
        if (ruleEvaluationInput.newsletterIndicatorPresent()
                || ruleEvaluationInput.listUnsubscribePresent()
                || isNoReplySender(ruleEvaluationInput.sanitizedSenderEmail())) {
            // Bulk / no-reply / automated mail is FYI; skip the LLM call entirely.
            return false;
        }
        try {
            return llmGateway.classifyReplyNeeded(
                    CallSite.NEEDS_REPLY, buildSemanticEvalContent(ruleEvaluationInput));
        } catch (RuntimeException needsReplyClassificationFailure) {
            log.warn(
                    "event=needs_reply_classification_failed tenantId={} reason={}",
                    tenantId,
                    needsReplyClassificationFailure.getClass().getSimpleName());
            return true;
        }
    }

    // local-part markers for automated senders that never expect a personal reply. Matched against
    // RuleEvaluationInput.sanitizedSenderEmail, which is already canonicalized to lower case.
    private static final List<String> NO_REPLY_LOCAL_PART_MARKERS =
            List.of(
                    "noreply",
                    "no-reply",
                    "no_reply",
                    "donotreply",
                    "do-not-reply",
                    "do_not_reply");

    private static boolean isNoReplySender(String sanitizedSenderEmail) {
        if (sanitizedSenderEmail == null || sanitizedSenderEmail.isBlank()) {
            return false;
        }
        int atIndex = sanitizedSenderEmail.indexOf('@');
        String localPart =
                atIndex > 0 ? sanitizedSenderEmail.substring(0, atIndex) : sanitizedSenderEmail;
        return NO_REPLY_LOCAL_PART_MARKERS.stream().anyMatch(localPart::contains);
    }

    /**
     * Outcome of the rules pipeline for one observed message: how many actions applied, and whether
     * a reply draft was saved (so the single reply-status classification can record it).
     */
    private record DispatchOutcome(int appliedActions, boolean draftSaved, String draftId) {
        static DispatchOutcome none() {
            return new DispatchOutcome(0, false, null);
        }
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
        // Single source of truth shared with the rule test/preview path so test ≡ runtime.
        return SemanticEvalContentBuilder.build(ruleEvaluationInput);
    }

    private void reserveDeterministicMessageCredit(UUID tenantId) {
        if (creditLedger.defaultCost(CallSite.TRIAGE_DETERMINISTIC) == 0) {
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
        // Defense-in-depth blast-radius cap: this is the LAST gate, so a slot is consumed only when
        // the send is otherwise greenlit. On deny we downgrade to a Gmail draft (no mail lost) and
        // keep the rest of this tenant's burst from flooding Gmail / burning credits.
        if (!outboundSendThrottle.acquire(dispatchContext.tenantId())) {
            return OUTBOUND_RATE_LIMITED;
        }
        // Outbound sends are gated only by the global Auto-send toggle and the user-managed sender
        // safety-net list. The former low-trust sender-anchor requirement was removed by product
        // decision so intent-only rules (e.g. "reply to any invite") can auto-send.
        return null;
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
