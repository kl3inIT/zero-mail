package com.zeromail.api.dto.triage;

import com.zeromail.core.triage.projection.AuditLogRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "auditId",
            "gmailThreadId",
            "gmailMessageId",
            "subject",
            "senderEmail",
            "ruleName",
            "action",
            "reason",
            "decisionState",
            "createdAt",
            "undoableUntil",
            "draftId"
        })
public record AuditEntryResponse(
        UUID auditId,
        String gmailThreadId,
        String gmailMessageId,
        @Schema(nullable = true) String subject,
        @Schema(nullable = true) String senderEmail,
        String ruleName,
        String action,
        String reason,
        String decisionState,
        Instant createdAt,
        Instant undoableUntil,
        @Schema(nullable = true) String draftId) {

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
