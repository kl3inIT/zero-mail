package com.zeromail.api.dto.triage;

import com.zeromail.core.triage.projection.AuditLogRow;
import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID auditId,
        String gmailThreadId,
        String gmailMessageId,
        String subject,
        String senderEmail,
        String ruleName,
        String action,
        String reason,
        String decisionState,
        Instant createdAt,
        Instant undoableUntil,
        String draftId) {

    public static AuditEntryResponse from(AuditLogRow row) {
        return new AuditEntryResponse(
                row.auditId(),
                row.gmailThreadId(),
                row.gmailMessageId(),
                row.sanitizedSubject(),
                row.sanitizedSenderEmail(),
                row.ruleName(),
                row.action(),
                row.reason(),
                row.decisionState(),
                row.createdAt(),
                row.undoableUntil(),
                row.draftId());
    }
}
