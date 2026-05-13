package com.zeromail.core.draft.usecases;

import com.zeromail.core.draft.domain.DraftStatus;
import com.zeromail.core.draft.exception.DraftGenerationFailedException;
import com.zeromail.core.draft.exception.DraftGenerationInFlightException;
import com.zeromail.core.draft.usecases.DraftReplySourceLoader.DraftReplySource;
import com.zeromail.core.llm.exception.SafetyViolationException;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.shared.lock.RedisDistributedLock;
import com.zeromail.core.shared.lock.RedisDistributedLock.LockHandle;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.thread.event.ThreadDraftSaved;
import com.zeromail.core.thread.persistence.ThreadReplyStatusRepository;
import com.zeromail.core.thread.usecases.ClassifyThreadReplyStatusService;
import com.zeromail.core.thread.usecases.ThreadReplyClassificationInput;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.exception.MissingMessageIdException;
import com.zeromail.core.triage.exception.ThreadingHeaderInvalidException;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import com.zeromail.core.triage.usecases.TriageActionResultJsonValidator;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GenerateThreadDraftService {

    private static final Logger log = LoggerFactory.getLogger(GenerateThreadDraftService.class);
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final UUID ON_DEMAND_DRAFT_RULE_ID =
            UUID.fromString("00000000-0000-0000-0000-00000005b003");
    private static final String ON_DEMAND_DRAFT_RULE_NAME = "On-demand draft";
    private static final String GENERATED_DRAFT_AUDIT_SENTINEL = "[generated]";
    private static final String NO_EXISTING_DRAFT_MARKER = "none";

    private final RedisDistributedLock redisDistributedLock;
    private final DraftReplySourceLoader draftReplySourceLoader;
    private final DraftBodyGenerator draftBodyGenerator;
    private final TriageGmailWriter triageGmailWriter;
    private final ThreadReplyStatusRepository threadReplyStatusRepository;
    private final ClassifyThreadReplyStatusService classifyThreadReplyStatusService;
    private final TriageAuditWriter triageAuditWriter;
    private final TriageAuditRepository triageAuditRepository;
    private final TriageActionResultJsonValidator actionResultJsonValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactionOperations;
    private final Clock clock;

    @Autowired
    public GenerateThreadDraftService(
            RedisDistributedLock redisDistributedLock,
            DraftReplySourceLoader draftReplySourceLoader,
            DraftBodyGenerator draftBodyGenerator,
            TriageGmailWriter triageGmailWriter,
            ThreadReplyStatusRepository threadReplyStatusRepository,
            ClassifyThreadReplyStatusService classifyThreadReplyStatusService,
            TriageAuditWriter triageAuditWriter,
            TriageAuditRepository triageAuditRepository,
            TriageActionResultJsonValidator actionResultJsonValidator,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this(
                redisDistributedLock,
                draftReplySourceLoader,
                draftBodyGenerator,
                triageGmailWriter,
                threadReplyStatusRepository,
                classifyThreadReplyStatusService,
                triageAuditWriter,
                triageAuditRepository,
                actionResultJsonValidator,
                eventPublisher,
                new TransactionTemplate(transactionManager),
                Clock.systemUTC());
    }

    public GenerateThreadDraftService(
            RedisDistributedLock redisDistributedLock,
            DraftReplySourceLoader draftReplySourceLoader,
            DraftBodyGenerator draftBodyGenerator,
            TriageGmailWriter triageGmailWriter,
            ThreadReplyStatusRepository threadReplyStatusRepository,
            ClassifyThreadReplyStatusService classifyThreadReplyStatusService,
            TriageAuditWriter triageAuditWriter,
            TriageAuditRepository triageAuditRepository,
            TriageActionResultJsonValidator actionResultJsonValidator,
            ApplicationEventPublisher eventPublisher,
            TransactionOperations transactionOperations,
            Clock clock) {
        this.redisDistributedLock =
                Objects.requireNonNull(
                        redisDistributedLock, "redisDistributedLock must not be null");
        this.draftReplySourceLoader =
                Objects.requireNonNull(
                        draftReplySourceLoader, "draftReplySourceLoader must not be null");
        this.draftBodyGenerator =
                Objects.requireNonNull(draftBodyGenerator, "draftBodyGenerator must not be null");
        this.triageGmailWriter =
                Objects.requireNonNull(triageGmailWriter, "triageGmailWriter must not be null");
        this.threadReplyStatusRepository =
                Objects.requireNonNull(
                        threadReplyStatusRepository,
                        "threadReplyStatusRepository must not be null");
        this.classifyThreadReplyStatusService =
                Objects.requireNonNull(
                        classifyThreadReplyStatusService,
                        "classifyThreadReplyStatusService must not be null");
        this.triageAuditWriter =
                Objects.requireNonNull(triageAuditWriter, "triageAuditWriter must not be null");
        this.triageAuditRepository =
                Objects.requireNonNull(
                        triageAuditRepository, "triageAuditRepository must not be null");
        this.actionResultJsonValidator =
                Objects.requireNonNull(
                        actionResultJsonValidator, "actionResultJsonValidator must not be null");
        this.eventPublisher =
                Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.transactionOperations =
                Objects.requireNonNull(
                        transactionOperations, "transactionOperations must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public GenerateThreadDraftResult generateOrRegenerate(GenerateThreadDraftCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return ScopedValue.where(TenantContext.TENANT, command.tenantId().toString())
                .call(() -> generateOrRegenerateWithTenantBound(command));
    }

    private GenerateThreadDraftResult generateOrRegenerateWithTenantBound(
            GenerateThreadDraftCommand command) {
        String lockKey = "draft:lock:" + command.tenantId() + ":" + command.gmailThreadId();
        LockHandle lockHandle =
                redisDistributedLock
                        .tryAcquire(lockKey, LOCK_TTL)
                        .orElseThrow(DraftGenerationInFlightException::new);
        try {
            String oldDraftId = currentDraftId(command.gmailThreadId()).orElse(null);
            DraftReplySource draftReplySource =
                    draftReplySourceLoader.load(command.tenantId(), command.gmailThreadId());
            String body =
                    draftBodyGenerator.generate(
                            command.tenantId(),
                            command.gmailThreadId(),
                            draftReplySource.inboundRawHtml(),
                            draftReplySource.inboundSubject());
            TriageActionResult.SaveDraft preWriteIntent =
                    preWriteIntent(command.gmailThreadId(), oldDraftId);
            UUID auditId =
                    reserveAuditBeforeGmailWrite(
                            command.tenantId(), draftReplySource, preWriteIntent);
            String newDraftId =
                    triageGmailWriter.saveDraft(
                            command.tenantId(),
                            draftReplySource.replyHeaders(),
                            body,
                            command.gmailThreadId());
            deleteOldDraftIfNeeded(
                    command.tenantId(), command.gmailThreadId(), oldDraftId, newDraftId);
            persistAuditAndClassify(
                    command.tenantId(), draftReplySource, newDraftId, auditId, preWriteIntent);
            eventPublisher.publishEvent(
                    new ThreadDraftSaved(
                            command.tenantId(),
                            command.gmailThreadId(),
                            newDraftId,
                            clock.instant()));
            DraftStatus status =
                    oldDraftId == null ? DraftStatus.GENERATED : DraftStatus.REGENERATED;
            log.info(
                    "event=draft_generated tenantId={} gmailThreadId={} status={}",
                    command.tenantId(),
                    command.gmailThreadId(),
                    status);
            return new GenerateThreadDraftResult(
                    newDraftId,
                    command.gmailThreadId(),
                    status,
                    "https://mail.google.com/mail/u/0/#all/" + command.gmailThreadId());
        } catch (SafetyViolationException safetyViolationException) {
            throw safetyViolationException;
        } catch (MissingMessageIdException
                | ThreadingHeaderInvalidException
                | IOException
                | IllegalArgumentException draftGenerationFailure) {
            throw new DraftGenerationFailedException(draftGenerationFailure);
        } finally {
            lockHandle.release();
        }
    }

    private UUID reserveAuditBeforeGmailWrite(
            UUID tenantId,
            DraftReplySource draftReplySource,
            TriageActionResult.SaveDraft preWriteIntent) {
        Optional<UUID> auditId =
                transactionOperations.execute(
                        _ ->
                                triageAuditWriter
                                        .insertPending(
                                                tenantId,
                                                draftReplySource.gmailMessageId(),
                                                draftReplySource.gmailThreadId(),
                                                ON_DEMAND_DRAFT_RULE_ID,
                                                ON_DEMAND_DRAFT_RULE_NAME,
                                                RuleActionType.SAVE_DRAFT,
                                                preWriteIntent,
                                                "on_demand_draft")
                                        .or(
                                                () ->
                                                        triageAuditWriter.findPendingAuditId(
                                                                tenantId,
                                                                draftReplySource.gmailMessageId(),
                                                                ON_DEMAND_DRAFT_RULE_ID,
                                                                RuleActionType.SAVE_DRAFT,
                                                                preWriteIntent)));
        return auditId.orElseThrow(DraftGenerationInFlightException::new);
    }

    private Optional<String> currentDraftId(String gmailThreadId) {
        return transactionOperations.execute(
                _ ->
                        threadReplyStatusRepository
                                .findByGmailThreadId(gmailThreadId)
                                .flatMap(
                                        threadReplyStatus ->
                                                Optional.ofNullable(
                                                        threadReplyStatus.getDraftId())));
    }

    private void deleteOldDraftIfNeeded(
            UUID tenantId, String gmailThreadId, String oldDraftId, String newDraftId) {
        if (oldDraftId == null || oldDraftId.equals(newDraftId)) {
            return;
        }
        try {
            triageGmailWriter.deleteDraft(tenantId, oldDraftId);
        } catch (IOException staleDraftDeleteFailure) {
            log.warn(
                    "event=stale_draft_delete_failed tenantId={} gmailThreadId={}",
                    tenantId,
                    gmailThreadId);
        }
    }

    private void persistAuditAndClassify(
            UUID tenantId,
            DraftReplySource draftReplySource,
            String newDraftId,
            UUID auditId,
            TriageActionResult.SaveDraft preWriteIntent) {
        transactionOperations.executeWithoutResult(
                _ -> {
                    TriageActionResult.SaveDraft resolvedIntent =
                            new TriageActionResult.SaveDraft(
                                    preWriteIntent.instruction(),
                                    newDraftId,
                                    draftReplySource.gmailThreadId());
                    triageAuditRepository.markApplied(
                            auditId,
                            tenantId,
                            newDraftId,
                            null,
                            actionResultJsonValidator.toJson(resolvedIntent));
                    classifyThreadReplyStatusService.classify(
                            new ThreadReplyClassificationInput(
                                    tenantId,
                                    draftReplySource.gmailThreadId(),
                                    draftReplySource.gmailMessageId(),
                                    draftReplySource.lastMessageFromIsTenant(),
                                    draftReplySource.threadHasSentLabel(),
                                    true,
                                    newDraftId,
                                    draftReplySource.lastMessageIsAutoReply()));
                });
    }

    private static TriageActionResult.SaveDraft preWriteIntent(
            String gmailThreadId, String oldDraftId) {
        String existingDraftMarker =
                oldDraftId == null || oldDraftId.isBlank() ? NO_EXISTING_DRAFT_MARKER : oldDraftId;
        return new TriageActionResult.SaveDraft(
                GENERATED_DRAFT_AUDIT_SENTINEL + ":existingDraft=" + existingDraftMarker,
                null,
                gmailThreadId);
    }
}
