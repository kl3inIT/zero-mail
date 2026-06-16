package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantListSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        requiredProperties = {
            "totalCount",
            "activeCount",
            "pausedCount",
            "disconnectedCount",
            "gmailConnectedCount",
            "telegramConnectedCount",
            "activeLast24hCount",
            "activeLast7dCount",
            "gmailUnhealthyCount",
            "automationFailure30dCount",
            "outboundBlocked30dCount",
            "lowCreditCount"
        })
public record TenantListSummaryResponse(
        int totalCount,
        int activeCount,
        int pausedCount,
        int disconnectedCount,
        int gmailConnectedCount,
        int telegramConnectedCount,
        int activeLast24hCount,
        int activeLast7dCount,
        int gmailUnhealthyCount,
        int automationFailure30dCount,
        int outboundBlocked30dCount,
        int lowCreditCount) {

    public static TenantListSummaryResponse from(TenantListSummary tenantListSummary) {
        return new TenantListSummaryResponse(
                tenantListSummary.totalCount(),
                tenantListSummary.activeCount(),
                tenantListSummary.pausedCount(),
                tenantListSummary.disconnectedCount(),
                tenantListSummary.gmailConnectedCount(),
                tenantListSummary.telegramConnectedCount(),
                tenantListSummary.activeLast24hCount(),
                tenantListSummary.activeLast7dCount(),
                tenantListSummary.gmailUnhealthyCount(),
                tenantListSummary.automationFailure30dCount(),
                tenantListSummary.outboundBlocked30dCount(),
                tenantListSummary.lowCreditCount());
    }
}
