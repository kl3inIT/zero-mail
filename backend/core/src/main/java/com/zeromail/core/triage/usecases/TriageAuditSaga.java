package com.zeromail.core.triage.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.ReplyHeaders;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.exception.MissingMessageIdException;
import com.zeromail.core.triage.exception.ThreadingHeaderInvalidException;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TriageAuditSaga {

    private static final Logger log = LoggerFactory.getLogger(TriageAuditSaga.class);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
    private static final String ARCHIVE_EXTERNAL_REF = "INBOX_REMOVED";

    private final TriageAuditWriter triageAuditWriter;
    private final TriageAuditRepository triageAuditRepository;
    private final TriageGmailWriter triageGmailWriter;

    public TriageAuditSaga(
            TriageAuditWriter triageAuditWriter,
            TriageAuditRepository triageAuditRepository,
            TriageGmailWriter triageGmailWriter) {
        this.triageAuditWriter = triageAuditWriter;
        this.triageAuditRepository = triageAuditRepository;
        this.triageGmailWriter = triageGmailWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservePhaseResult reservePhase(TriageAuditCommand command, String leaseOwner) {
        Objects.requireNonNull(command, "command must not be null");
        requireText(leaseOwner, "leaseOwner");

        Optional<UUID> pendingAuditId =
                triageAuditWriter
                        .insertPending(
                                command.tenantId(),
                                command.gmailMessageId(),
                                command.gmailThreadId(),
                                command.ruleId(),
                                command.ruleNameSnapshot(),
                                command.actionType(),
                                command.preWriteIntent(),
                                command.reasonEvidence())
                        .or(
                                () ->
                                        triageAuditWriter.findPendingAuditId(
                                                command.tenantId(),
                                                command.gmailMessageId(),
                                                command.ruleId(),
                                                command.actionType(),
                                                command.preWriteIntent()));
        if (pendingAuditId.isEmpty()) {
            log.info(
                    "event=triage_audit_pending_skip tenantId={} gmailMessageId={} ruleId={} actionType={}",
                    command.tenantId(),
                    command.gmailMessageId(),
                    command.ruleId(),
                    command.actionType());
            return ReservePhaseResult.skipped();
        }

        UUID auditId = pendingAuditId.get();
        int reclaimedRows =
                triageAuditRepository.reclaimStalePending(auditId, command.tenantId(), leaseOwner);
        if (reclaimedRows == 0) {
            log.info(
                    "event=triage_audit_pending_in_flight tenantId={} gmailMessageId={} auditId={}",
                    command.tenantId(),
                    command.gmailMessageId(),
                    auditId);
            return ReservePhaseResult.skipped();
        }

        log.info(
                "event=triage_audit_pending_reserved tenantId={} gmailMessageId={} auditId={}",
                command.tenantId(),
                command.gmailMessageId(),
                auditId);
        return ReservePhaseResult.reserved(auditId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GmailWriteResult gmailWritePhase(TriageAuditCommand command) throws IOException {
        Objects.requireNonNull(command, "command must not be null");
        return switch (command.preWriteIntent()) {
            case TriageActionResult.Label label -> {
                String resolvedLabelId =
                        triageGmailWriter.applyLabel(
                                command.tenantId(), command.gmailMessageId(), label.labelName());
                TriageActionResult.Label resolvedLabel =
                        new TriageActionResult.Label(resolvedLabelId, label.labelName());
                yield GmailWriteResult.applied(
                        command.gmailMessageId(),
                        changeToken(Map.of("addedLabelId", resolvedLabelId)),
                        actionArgsJson(resolvedLabel));
            }
            case TriageActionResult.Archive ignored -> {
                triageGmailWriter.archiveSkipInbox(command.tenantId(), command.gmailMessageId());
                yield GmailWriteResult.applied(
                        ARCHIVE_EXTERNAL_REF,
                        changeToken(Map.of("removedLabelIds", List.of("INBOX"))),
                        null);
            }
            case TriageActionResult.SaveDraft saveDraft -> {
                try {
                    String draftId =
                            triageGmailWriter.saveDraft(
                                    command.tenantId(),
                                    command.replyHeaders(),
                                    saveDraft.instruction(),
                                    command.gmailThreadId());
                    yield GmailWriteResult.applied(
                            draftId,
                            null,
                            actionArgsJson(
                                    new TriageActionResult.SaveDraft(
                                            saveDraft.instruction(),
                                            draftId,
                                            saveDraft.threadId())));
                } catch (MissingMessageIdException
                        | ThreadingHeaderInvalidException threadingException) {
                    yield GmailWriteResult.failed("draft_threading_invalid");
                }
            }
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizePhase(UUID tenantId, UUID auditId, GmailWriteResult gmailWriteResult) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(auditId, "auditId must not be null");
        Objects.requireNonNull(gmailWriteResult, "gmailWriteResult must not be null");

        if (gmailWriteResult.applied()) {
            triageAuditRepository.markApplied(
                    auditId,
                    tenantId,
                    gmailWriteResult.externalRef(),
                    gmailWriteResult.gmailChangeToken(),
                    gmailWriteResult.resolvedActionArgsJson());
            log.info("event=triage_audit_applied tenantId={} auditId={}", tenantId, auditId);
            return;
        }

        triageAuditRepository.markFailed(auditId, tenantId, gmailWriteResult.failureReason());
        log.info("event=triage_audit_failed tenantId={} auditId={}", tenantId, auditId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> recordTerminal(
            TriageAuditCommand command, TriageDecision terminalDecision) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(terminalDecision, "terminalDecision must not be null");
        Optional<UUID> auditId =
                triageAuditWriter.insertTerminal(
                        command.tenantId(),
                        command.gmailMessageId(),
                        command.gmailThreadId(),
                        command.ruleId(),
                        command.ruleNameSnapshot(),
                        command.actionType(),
                        command.preWriteIntent(),
                        command.reasonEvidence(),
                        terminalDecision);
        log.info(
                "event=triage_audit_terminal tenantId={} gmailMessageId={} ruleId={} actionType={} decision={}",
                command.tenantId(),
                command.gmailMessageId(),
                command.ruleId(),
                command.actionType(),
                terminalDecision);
        return auditId;
    }

    private static String changeToken(Map<String, ?> fields) {
        try {
            return OBJECT_MAPPER.writeValueAsString(fields);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "Unable to serialize Gmail change token", jacksonException);
        }
    }

    private static String actionArgsJson(TriageActionResult actionResult) {
        return new TriageActionResultJsonValidator().toJson(actionResult);
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    public record TriageAuditCommand(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            UUID ruleId,
            String ruleNameSnapshot,
            RuleActionType actionType,
            TriageActionResult preWriteIntent,
            ReplyHeaders replyHeaders,
            String reasonEvidence) {

        public TriageAuditCommand {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            requireText(gmailMessageId, "gmailMessageId");
            requireText(gmailThreadId, "gmailThreadId");
            Objects.requireNonNull(ruleId, "ruleId must not be null");
            requireText(ruleNameSnapshot, "ruleNameSnapshot");
            Objects.requireNonNull(actionType, "actionType must not be null");
            Objects.requireNonNull(preWriteIntent, "preWriteIntent must not be null");
            if (preWriteIntent instanceof TriageActionResult.SaveDraft) {
                Objects.requireNonNull(replyHeaders, "replyHeaders must not be null");
            }
            requireText(reasonEvidence, "reasonEvidence");
        }
    }

    public record ReservePhaseResult(Optional<UUID> auditId, boolean shouldAttemptGmail) {

        public ReservePhaseResult {
            auditId = Objects.requireNonNullElseGet(auditId, Optional::empty);
        }

        static ReservePhaseResult skipped() {
            return new ReservePhaseResult(Optional.empty(), false);
        }

        static ReservePhaseResult reserved(UUID auditId) {
            return new ReservePhaseResult(Optional.of(auditId), true);
        }
    }

    public record GmailWriteResult(
            boolean applied,
            String externalRef,
            String gmailChangeToken,
            String resolvedActionArgsJson,
            String failureReason) {

        static GmailWriteResult applied(
                String externalRef, String gmailChangeToken, String resolvedActionArgsJson) {
            return new GmailWriteResult(
                    true, externalRef, gmailChangeToken, resolvedActionArgsJson, null);
        }

        public static GmailWriteResult failed(String failureReason) {
            return new GmailWriteResult(
                    false, null, null, null, requireText(failureReason, "failureReason"));
        }
    }
}
