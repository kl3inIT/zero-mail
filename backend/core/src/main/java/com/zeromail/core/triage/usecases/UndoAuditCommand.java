package com.zeromail.core.triage.usecases;

import java.util.UUID;

public record UndoAuditCommand(UUID auditId, UUID tenantId) {

    public UndoAuditCommand {
        if (auditId == null) {
            throw new IllegalArgumentException("auditId must not be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
    }
}
