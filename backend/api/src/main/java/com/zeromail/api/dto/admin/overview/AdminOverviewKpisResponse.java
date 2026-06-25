package com.zeromail.api.dto.admin.overview;

import com.zeromail.core.admin.overview.projection.AdminOverviewKpis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(
        requiredProperties = {
            "totalTenants",
            "gmailConnectedTenants",
            "activeLast7dTenants",
            "observedEmailCount",
            "triageActionCount",
            "failedTriageActionCount",
            "outboundActionCount",
            "blockedOutboundActionCount",
            "llmCallCount",
            "llmChargedCredits",
            "llmCostUsd",
            "gmailUnhealthyTenants",
            "pubsubBacklogCount",
            "deadLetterJobCount",
            "lowCreditTenantCount"
        })
public record AdminOverviewKpisResponse(
        int totalTenants,
        int gmailConnectedTenants,
        int activeLast7dTenants,
        int observedEmailCount,
        int triageActionCount,
        int failedTriageActionCount,
        int outboundActionCount,
        int blockedOutboundActionCount,
        int llmCallCount,
        long llmChargedCredits,
        BigDecimal llmCostUsd,
        int gmailUnhealthyTenants,
        int pubsubBacklogCount,
        int deadLetterJobCount,
        int lowCreditTenantCount) {

    public static AdminOverviewKpisResponse from(AdminOverviewKpis kpis) {
        return new AdminOverviewKpisResponse(
                kpis.totalTenants(),
                kpis.gmailConnectedTenants(),
                kpis.activeLast7dTenants(),
                kpis.observedEmailCount(),
                kpis.triageActionCount(),
                kpis.failedTriageActionCount(),
                kpis.outboundActionCount(),
                kpis.blockedOutboundActionCount(),
                kpis.llmCallCount(),
                kpis.llmChargedCredits(),
                kpis.llmCostUsd(),
                kpis.gmailUnhealthyTenants(),
                kpis.pubsubBacklogCount(),
                kpis.deadLetterJobCount(),
                kpis.lowCreditTenantCount());
    }
}
