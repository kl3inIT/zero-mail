package com.zeromail.core.triage.usecases;

import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.domain.TriageUndoPolicy;
import com.zeromail.core.triage.exception.TriageAuditException;
import com.zeromail.core.triage.exception.TriageAuditNotFoundException;
import com.zeromail.core.triage.exception.TriageUndoException;
import com.zeromail.core.triage.persistence.TriageAuditEntity;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TriageUndoService {

    private static final Logger log = LoggerFactory.getLogger(TriageUndoService.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final String INBOX_LABEL_ID = "INBOX";

    private final TriageAuditRepository triageAuditRepository;
    private final TriageGmailWriter triageGmailWriter;
    private final TriageActionResultJsonValidator triageActionResultJsonValidator;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Autowired
    public TriageUndoService(
            TriageAuditRepository triageAuditRepository,
            TriageGmailWriter triageGmailWriter,
            TriageActionResultJsonValidator triageActionResultJsonValidator,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(
                triageAuditRepository,
                triageGmailWriter,
                triageActionResultJsonValidator,
                Clock.systemUTC(),
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new));
    }

    TriageUndoService(
            TriageAuditRepository triageAuditRepository,
            TriageGmailWriter triageGmailWriter,
            TriageActionResultJsonValidator triageActionResultJsonValidator,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.triageAuditRepository = triageAuditRepository;
        this.triageGmailWriter = triageGmailWriter;
        this.triageActionResultJsonValidator = triageActionResultJsonValidator;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UndoAuditResult undo(UndoAuditCommand command) {
        UndoPreparation undoPreparation = prepareUndo(command);
        TriageAuditEntity auditRow = undoPreparation.auditRow();
        RuleActionType actionType = undoPreparation.actionType();
        TriageActionResult actionResult = undoPreparation.actionResult();

        try {
            executeInverse(auditRow, actionResult);
        } catch (IOException gmailWriteFailure) {
            log.warn(
                    "event=triage_action_revert_failed tenantId={} auditId={} errorClass={}",
                    command.tenantId(),
                    command.auditId(),
                    gmailWriteFailure.getClass().getSimpleName());
            throw TriageUndoException.writeFailed();
        }

        Instant revertedAt = clock.instant();
        int revertedRows =
                triageAuditRepository.markReverted(
                        command.auditId(), command.tenantId(), revertedAt);
        if (revertedRows != 1) {
            throw TriageUndoException.alreadyDone();
        }

        Instant appliedAt = undoPreparation.appliedAt();
        long daysAfterApply = Math.max(0L, ChronoUnit.DAYS.between(appliedAt, revertedAt));
        meterRegistry
                .counter(
                        "triage.audit.undo",
                        "action_type",
                        actionType.id(),
                        "days_after_apply_bucket",
                        daysAfterApplyBucket(daysAfterApply))
                .increment();
        log.info(
                "event=triage_action_reverted tenantId={} auditId={} actionType={} daysAfterApply={}",
                command.tenantId(),
                command.auditId(),
                actionType.id(),
                daysAfterApply);

        return new UndoAuditResult(command.auditId(), TriageDecision.REVERTED, revertedAt);
    }

    private UndoPreparation prepareUndo(UndoAuditCommand command) {
        TriageAuditEntity auditRow =
                triageAuditRepository
                        .findByAuditIdAndTenantId(command.auditId(), command.tenantId())
                        .orElseThrow(TriageAuditNotFoundException::new);

        TriageDecision decision = auditRow.getDecision();
        if (decision != TriageDecision.APPLIED && decision != TriageDecision.REVERT_PENDING) {
            throw TriageUndoException.alreadyDone();
        }

        Instant appliedAt = auditRow.getAppliedAt();
        Instant now = clock.instant();
        if (appliedAt == null || appliedAt.isBefore(now.minus(TriageUndoPolicy.UNDO_WINDOW))) {
            throw TriageUndoException.expired();
        }

        RuleActionType actionType = actionType(auditRow);
        TriageActionResult actionResult = actionResult(auditRow);
        validateActionTypeMatchesResult(actionType, actionResult);

        if (decision == TriageDecision.APPLIED) {
            int revertPendingRows =
                    triageAuditRepository.markRevertPending(command.auditId(), command.tenantId());
            if (revertPendingRows != 1) {
                auditRow =
                        triageAuditRepository
                                .findByAuditIdAndTenantId(command.auditId(), command.tenantId())
                                .orElseThrow(TriageAuditNotFoundException::new);
                if (auditRow.getDecision() != TriageDecision.REVERT_PENDING) {
                    throw TriageUndoException.alreadyDone();
                }
            }
        }

        return new UndoPreparation(auditRow, actionType, actionResult, appliedAt);
    }

    private void executeInverse(TriageAuditEntity auditRow, TriageActionResult actionResult)
            throws IOException {
        MailboxRef executingMailboxRef =
                new MailboxRef(auditRow.getTenantId(), auditRow.getExecutingMailboxId());
        switch (actionResult) {
            case TriageActionResult.Label _ ->
                    triageGmailWriter.removeLabel(
                            executingMailboxRef,
                            auditRow.getGmailMessageId(),
                            requiredAddedLabelId(auditRow.getGmailChangeToken()));
            case TriageActionResult.Archive _ -> {
                requireArchiveChangeToken(auditRow.getGmailChangeToken());
                triageGmailWriter.restoreToInbox(executingMailboxRef, auditRow.getGmailMessageId());
            }
            case TriageActionResult.SaveDraft saveDraft ->
                    triageGmailWriter.deleteDraft(
                            executingMailboxRef, draftId(auditRow, saveDraft));
            case TriageActionResult.MarkRead _ ->
                    triageGmailWriter.markUnread(executingMailboxRef, auditRow.getGmailMessageId());
            case TriageActionResult.Star _ ->
                    triageGmailWriter.unstar(executingMailboxRef, auditRow.getGmailMessageId());
            case TriageActionResult.AddToDigest _ ->
                    triageGmailWriter.removeLabel(
                            executingMailboxRef,
                            auditRow.getGmailMessageId(),
                            requiredAddedLabelId(auditRow.getGmailChangeToken()));
            case TriageActionResult.MarkSpam _ ->
                    triageGmailWriter.unmarkSpam(executingMailboxRef, auditRow.getGmailMessageId());
            case TriageActionResult.SendReply _ ->
                    throw TriageAuditException.unsupportedActionType();
            case TriageActionResult.ForwardEmail _ ->
                    throw TriageAuditException.unsupportedActionType();
            case TriageActionResult.SendEmail _ ->
                    throw TriageAuditException.unsupportedActionType();
        }
    }

    private RuleActionType actionType(TriageAuditEntity auditRow) {
        try {
            return auditRow.getActionType();
        } catch (IllegalArgumentException | NoSuchElementException unsupportedActionType) {
            throw TriageAuditException.unsupportedActionType();
        }
    }

    private TriageActionResult actionResult(TriageAuditEntity auditRow) {
        try {
            return triageActionResultJsonValidator.parseActionArgsJson(
                    auditRow.getActionArgsJson());
        } catch (IllegalArgumentException | NoSuchElementException unsupportedActionType) {
            throw TriageAuditException.unsupportedActionType();
        }
    }

    private static void validateActionTypeMatchesResult(
            RuleActionType actionType, TriageActionResult actionResult) {
        boolean matches =
                switch (actionResult) {
                    case TriageActionResult.Label _ -> actionType == RuleActionType.LABEL;
                    case TriageActionResult.Archive _ -> actionType == RuleActionType.ARCHIVE;
                    case TriageActionResult.SaveDraft _ -> actionType == RuleActionType.SAVE_DRAFT;
                    case TriageActionResult.MarkRead _ -> actionType == RuleActionType.MARK_READ;
                    case TriageActionResult.Star _ -> actionType == RuleActionType.STAR;
                    case TriageActionResult.AddToDigest _ ->
                            actionType == RuleActionType.ADD_TO_DIGEST;
                    case TriageActionResult.MarkSpam _ -> actionType == RuleActionType.MARK_SPAM;
                    case TriageActionResult.SendReply _ -> actionType == RuleActionType.SEND_REPLY;
                    case TriageActionResult.ForwardEmail _ ->
                            actionType == RuleActionType.FORWARD_EMAIL;
                    case TriageActionResult.SendEmail _ -> actionType == RuleActionType.SEND_EMAIL;
                };
        if (!matches) {
            throw TriageAuditException.unsupportedActionType();
        }
    }

    private static String requiredAddedLabelId(String gmailChangeToken) {
        JsonNode changeTokenNode = readRequiredChangeToken(gmailChangeToken);
        JsonNode addedLabelNode = changeTokenNode.path("addedLabelId");
        if (addedLabelNode.isMissingNode()
                || addedLabelNode.isNull()
                || !addedLabelNode.isString()
                || addedLabelNode.asString().isBlank()) {
            throw TriageAuditException.unsupportedActionType();
        }
        return addedLabelNode.asString();
    }

    private static void requireArchiveChangeToken(String gmailChangeToken) {
        JsonNode changeTokenNode = readRequiredChangeToken(gmailChangeToken);
        JsonNode removedLabelIdsNode = changeTokenNode.path("removedLabelIds");
        if (!removedLabelIdsNode.isArray()) {
            throw TriageAuditException.unsupportedActionType();
        }
        for (JsonNode removedLabelNode : removedLabelIdsNode) {
            if (removedLabelNode.isString() && INBOX_LABEL_ID.equals(removedLabelNode.asString())) {
                return;
            }
        }
        throw TriageAuditException.unsupportedActionType();
    }

    private static JsonNode readRequiredChangeToken(String gmailChangeToken) {
        if (gmailChangeToken == null || gmailChangeToken.isBlank()) {
            throw TriageAuditException.unsupportedActionType();
        }
        try {
            JsonNode changeTokenNode = OBJECT_MAPPER.readTree(gmailChangeToken);
            if (!changeTokenNode.isObject()) {
                throw TriageAuditException.unsupportedActionType();
            }
            return changeTokenNode;
        } catch (JacksonException jacksonException) {
            throw TriageAuditException.unsupportedActionType();
        }
    }

    private static String draftId(
            TriageAuditEntity auditRow, TriageActionResult.SaveDraft saveDraft) {
        String externalReference = auditRow.getExternalRef();
        if (externalReference != null && !externalReference.isBlank()) {
            return externalReference;
        }
        if (saveDraft.draftId() != null && !saveDraft.draftId().isBlank()) {
            return saveDraft.draftId();
        }
        throw TriageAuditException.unsupportedActionType();
    }

    private static String daysAfterApplyBucket(long daysAfterApply) {
        if (daysAfterApply == 0) {
            return "0";
        }
        if (daysAfterApply <= 7) {
            return "1_7";
        }
        if (daysAfterApply <= 30) {
            return "8_30";
        }
        return "over_30";
    }

    private record UndoPreparation(
            TriageAuditEntity auditRow,
            RuleActionType actionType,
            TriageActionResult actionResult,
            Instant appliedAt) {}
}
