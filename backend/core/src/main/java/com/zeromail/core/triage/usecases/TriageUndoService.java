package com.zeromail.core.triage.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.domain.TriageActionResultJsonValidator;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.exception.TriageAuditException;
import com.zeromail.core.triage.exception.TriageAuditNotFoundException;
import com.zeromail.core.triage.exception.TriageUndoAlreadyDoneException;
import com.zeromail.core.triage.exception.TriageUndoExpiredException;
import com.zeromail.core.triage.exception.TriageUndoWriteFailedException;
import com.zeromail.core.triage.persistence.TriageAuditEntity;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TriageUndoService {

    private static final Logger log = LoggerFactory.getLogger(TriageUndoService.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final Duration UNDO_WINDOW = Duration.ofDays(30);
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

    @Transactional
    public UndoAuditResult undo(UndoAuditCommand command) {
        TriageAuditEntity auditRow =
                triageAuditRepository
                        .findByAuditIdAndTenantId(command.auditId(), command.tenantId())
                        .orElseThrow(TriageAuditNotFoundException::new);

        if (auditRow.getDecision() != TriageDecision.APPLIED) {
            throw new TriageUndoAlreadyDoneException();
        }

        Instant appliedAt = auditRow.getAppliedAt();
        Instant revertedAt = clock.instant();
        if (appliedAt == null || appliedAt.isBefore(revertedAt.minus(UNDO_WINDOW))) {
            throw new TriageUndoExpiredException();
        }

        RuleActionType actionType = actionType(auditRow);
        TriageActionResult actionResult = actionResult(auditRow);
        validateActionTypeMatchesResult(actionType, actionResult);

        try {
            executeInverse(command.tenantId(), auditRow, actionResult);
        } catch (IOException gmailWriteFailure) {
            log.warn(
                    "event=triage_action_revert_failed tenantId={} auditId={} errorClass={}",
                    command.tenantId(),
                    command.auditId(),
                    gmailWriteFailure.getClass().getSimpleName());
            throw new TriageUndoWriteFailedException();
        }

        int revertedRows =
                triageAuditRepository.markReverted(
                        command.auditId(), command.tenantId(), revertedAt);
        if (revertedRows != 1) {
            throw new TriageUndoAlreadyDoneException();
        }

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

    private void executeInverse(
            UUID tenantId, TriageAuditEntity auditRow, TriageActionResult actionResult)
            throws IOException {
        switch (actionResult) {
            case TriageActionResult.Label ignored ->
                    triageGmailWriter.removeLabel(
                            tenantId,
                            auditRow.getGmailMessageId(),
                            requiredAddedLabelId(auditRow.getGmailChangeToken()));
            case TriageActionResult.Archive ignored -> {
                requireArchiveChangeToken(auditRow.getGmailChangeToken());
                triageGmailWriter.restoreToInbox(tenantId, auditRow.getGmailMessageId());
            }
            case TriageActionResult.SaveDraft saveDraft ->
                    triageGmailWriter.deleteDraft(tenantId, draftId(auditRow, saveDraft));
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
                    case TriageActionResult.Label ignored -> actionType == RuleActionType.LABEL;
                    case TriageActionResult.Archive ignored -> actionType == RuleActionType.ARCHIVE;
                    case TriageActionResult.SaveDraft ignored ->
                            actionType == RuleActionType.SAVE_DRAFT;
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
}
