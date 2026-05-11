package com.zeromail.core.triage.usecases;

import com.zeromail.core.triage.domain.TriageDecision;
import java.time.Instant;
import java.util.UUID;

public record UndoAuditResult(UUID auditId, TriageDecision decision, Instant revertedAt) {

    public UndoAuditResult {
        if (auditId == null) {
            throw new IllegalArgumentException("auditId must not be null");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (revertedAt == null) {
            throw new IllegalArgumentException("revertedAt must not be null");
        }
    }
}
