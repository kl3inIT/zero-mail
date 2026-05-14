package com.zeromail.api.dto.triage;

import com.zeromail.core.triage.projection.AuditLogPage;
import java.util.List;

public record AuditListResponse(List<AuditEntryResponse> items, String nextCursor) {

    public AuditListResponse {
        items = List.copyOf(items);
    }

    public static AuditListResponse from(AuditLogPage page) {
        return new AuditListResponse(
                page.items().stream().map(AuditEntryResponse::from).toList(), page.nextCursor());
    }
}
