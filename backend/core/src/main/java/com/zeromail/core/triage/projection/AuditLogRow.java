package com.zeromail.core.triage.projection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditLogRow(
        UUID auditId,
        String gmailThreadId,
        String gmailMessageId,
        String sanitizedSubject,
        String sanitizedSenderEmail,
        String ruleName,
        String action,
        String reason,
        String decisionState,
        Instant createdAt,
        Instant undoableUntil,
        String draftId) {

    public AuditLogRow {
        Objects.requireNonNull(auditId, "auditId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(decisionState, "decisionState must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
