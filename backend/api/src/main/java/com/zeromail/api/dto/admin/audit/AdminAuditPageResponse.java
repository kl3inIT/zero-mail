package com.zeromail.api.dto.admin.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.audit.projection.AdminAuditPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"rows", "hasNextPage", "totalEstimate"})
public record AdminAuditPageResponse(
        List<AdminAuditEventResponse> rows,
        String nextCursor,
        boolean hasNextPage,
        long totalEstimate) {

    public AdminAuditPageResponse {
        rows = List.copyOf(rows);
    }

    public static AdminAuditPageResponse from(AdminAuditPage auditPage, int nextOffset) {
        String nextCursor = auditPage.hasNextPage() ? String.valueOf(nextOffset) : null;
        return new AdminAuditPageResponse(
                auditPage.items().stream().map(AdminAuditEventResponse::from).toList(),
                nextCursor,
                auditPage.hasNextPage(),
                auditPage.items().size());
    }
}
