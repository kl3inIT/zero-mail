package com.zeromail.core.triage.usecases;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TriageDraftAuditService {

    private final TriageAuditWriter triageAuditWriter;
    private final TriageAuditRepository triageAuditRepository;
    private final TriageActionResultJsonValidator actionResultJsonValidator;

    public TriageDraftAuditService(
            TriageAuditWriter triageAuditWriter,
            TriageAuditRepository triageAuditRepository,
            TriageActionResultJsonValidator actionResultJsonValidator) {
        this.triageAuditWriter = triageAuditWriter;
        this.triageAuditRepository = triageAuditRepository;
        this.actionResultJsonValidator = actionResultJsonValidator;
    }

    @Transactional
    public TriageDraftAuditReservation reservePendingAudit(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSubject,
            String sanitizedSenderEmail,
            UUID ruleId,
            String ruleNameSnapshot,
            RuleActionType actionType,
            TriageActionResult preWriteIntent,
            String reasonEvidence,
            String leaseOwner) {
        Optional<UUID> pendingAuditId =
                triageAuditWriter
                        .insertPending(
                                tenantId,
                                gmailMessageId,
                                gmailThreadId,
                                sanitizedSubject,
                                sanitizedSenderEmail,
                                ruleId,
                                ruleNameSnapshot,
                                actionType,
                                preWriteIntent,
                                reasonEvidence)
                        .or(
                                () ->
                                        triageAuditWriter.findPendingAuditId(
                                                tenantId,
                                                gmailMessageId,
                                                ruleId,
                                                actionType,
                                                preWriteIntent));
        if (pendingAuditId.isEmpty()) {
            return new TriageDraftAuditReservation(null, false);
        }
        UUID auditId = pendingAuditId.orElseThrow();
        int reclaimedRows =
                triageAuditRepository.reclaimStalePending(auditId, tenantId, leaseOwner);
        return new TriageDraftAuditReservation(auditId, reclaimedRows > 0);
    }

    @Transactional
    public void markApplied(
            UUID auditId,
            UUID tenantId,
            String externalReference,
            TriageActionResult resolvedIntent) {
        triageAuditRepository.markApplied(
                auditId,
                tenantId,
                externalReference,
                null,
                actionResultJsonValidator.toJson(resolvedIntent));
    }

    public record TriageDraftAuditReservation(UUID auditId, boolean reserved) {}
}
