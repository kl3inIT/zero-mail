package com.zeromail.api.dto.triage;

import com.zeromail.core.triage.projection.AuditLogPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "nextCursor"})
public record AuditListResponse(
        List<AuditEntryResponse> items, @Schema(nullable = true) String nextCursor) {

    public AuditListResponse {
        items = List.copyOf(items);
    }

    public static AuditListResponse from(AuditLogPage page) {
        return new AuditListResponse(
                page.items().stream().map(AuditEntryResponse::from).toList(), page.nextCursor());
    }
}
