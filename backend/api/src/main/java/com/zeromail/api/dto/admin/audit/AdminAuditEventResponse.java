package com.zeromail.api.dto.admin.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.audit.projection.AdminAuditRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"auditId", "chainIndex", "actorEmail", "action", "createdAt"})
public record AdminAuditEventResponse(
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

    public static AdminAuditEventResponse from(AdminAuditRow auditRow) {
        return new AdminAuditEventResponse(
                auditRow.auditId(),
                auditRow.chainIndex(),
                auditRow.actorEmail(),
                auditRow.action(),
                auditRow.targetKind(),
                auditRow.targetId(),
                auditRow.reason(),
                auditRow.requestIp(),
                auditRow.requestId(),
                auditRow.createdAt());
    }
}
