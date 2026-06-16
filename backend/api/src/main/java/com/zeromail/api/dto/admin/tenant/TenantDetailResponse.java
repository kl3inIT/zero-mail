package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantDetailOverview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "tenantId",
            "createdAt",
            "status",
            "gmailConnectionStatus",
            "telegramStatus",
            "rulesCount",
            "enabledRulesCount",
            "enabledRuleNames"
        })
public record TenantDetailResponse(
        UUID tenantId,
        Instant createdAt,
        String gmailAccountEmail,
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
                tenantDetailOverview.createdAt(),
                tenantDetailOverview.gmailAccountEmail(),
                tenantDetailOverview.status(),
                tenantDetailOverview.gmailConnectionStatus(),
                tenantDetailOverview.telegramStatus(),
                tenantDetailOverview.lastActivityAt(),
                tenantDetailOverview.rulesCount(),
                tenantDetailOverview.enabledRulesCount(),
                tenantDetailOverview.enabledRuleNames());
    }
}
