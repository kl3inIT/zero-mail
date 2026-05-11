package com.zeromail.api.dto.triage;

import com.zeromail.core.triage.usecases.UndoAuditResult;
import java.time.Instant;
import java.util.UUID;

public record UndoAuditResponse(UUID auditId, String decision, Instant revertedAt) {

    public static UndoAuditResponse from(UndoAuditResult undoAuditResult) {
        return new UndoAuditResponse(
                undoAuditResult.auditId(),
                undoAuditResult.decision().id(),
                undoAuditResult.revertedAt());
    }
}
