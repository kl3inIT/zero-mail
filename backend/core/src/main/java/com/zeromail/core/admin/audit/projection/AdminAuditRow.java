package com.zeromail.core.admin.audit.projection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdminAuditRow(
        UUID auditId,
        long chainIndex,
        String actorEmail,
        String action,
        String targetKind,
        UUID targetId,
        String reason,
        String requestIp,
        UUID requestId,
        Instant createdAt) {

    public AdminAuditRow {
        Objects.requireNonNull(auditId, "auditId must not be null");
        Objects.requireNonNull(actorEmail, "actorEmail must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
