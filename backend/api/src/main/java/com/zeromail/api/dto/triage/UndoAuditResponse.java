package com.zeromail.api.dto.triage;

import java.time.Instant;
import java.util.UUID;

import com.zeromail.core.triage.application.UndoAuditResult;

public record UndoAuditResponse(UUID auditId, String decision, Instant revertedAt) {

  public static UndoAuditResponse from(UndoAuditResult undoAuditResult) {
    return new UndoAuditResponse(
        undoAuditResult.auditId(), undoAuditResult.decision().id(), undoAuditResult.revertedAt());
  }
}
