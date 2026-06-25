package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantDetailOverview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "tenantId",
            "tenantDisplayName",
            "createdAt",
            "status",
            "gmailConnectionStatus",
            "telegramStatus",
            "gmailAccountCount",
            "connectedGmailAccountCount",
            "rulesCount",
            "enabledRulesCount",
            "enabledRuleNames"
        })
public record TenantDetailResponse(
        UUID tenantId,
        String tenantDisplayName,
        @Schema(nullable = true) String ownerEmail,
        Instant createdAt,
        @Schema(nullable = true) String gmailAccountEmail,
        int gmailAccountCount,
        int connectedGmailAccountCount,
        @Schema(allowableValues = {"ACTIVE", "PAUSED", "DISCONNECTED"}) String status,
        @Schema(allowableValues = {"CONNECTED", "DISCONNECTED"}) String gmailConnectionStatus,
        @Schema(allowableValues = {"CONNECTED", "BLOCKED", "DISCONNECTED", "NO_CONNECTION"})
                String telegramStatus,
        Instant lastActivityAt,
        int rulesCount,
        int enabledRulesCount,
        List<String> enabledRuleNames) {

    public static TenantDetailResponse from(TenantDetailOverview tenantDetailOverview) {
        return new TenantDetailResponse(
                tenantDetailOverview.tenantId(),
                tenantDetailOverview.tenantDisplayName(),
                tenantDetailOverview.ownerEmail(),
                tenantDetailOverview.createdAt(),
                tenantDetailOverview.gmailAccountEmail(),
                tenantDetailOverview.gmailAccountCount(),
                tenantDetailOverview.connectedGmailAccountCount(),
                tenantDetailOverview.status(),
                tenantDetailOverview.gmailConnectionStatus(),
                tenantDetailOverview.telegramStatus(),
                tenantDetailOverview.lastActivityAt(),
                tenantDetailOverview.rulesCount(),
                tenantDetailOverview.enabledRulesCount(),
                tenantDetailOverview.enabledRuleNames());
    }
}
