package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantDeletionPreview;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        requiredProperties = {
            "gmailConnections",
            "chatSessions",
            "rules",
            "triageAudits",
            "chatMessages",
            "byokCredentials"
        })
public record TenantDeletionPreviewResponse(
        int gmailConnections,
        int chatSessions,
        int rules,
        int triageAudits,
        int chatMessages,
        int byokCredentials) {

    public static TenantDeletionPreviewResponse from(TenantDeletionPreview tenantDeletionPreview) {
        return new TenantDeletionPreviewResponse(
                tenantDeletionPreview.gmailConnections(),
                tenantDeletionPreview.chatSessions(),
                tenantDeletionPreview.rules(),
                tenantDeletionPreview.triageAudits(),
                tenantDeletionPreview.chatMessages(),
                tenantDeletionPreview.byokCredentials());
    }
}
