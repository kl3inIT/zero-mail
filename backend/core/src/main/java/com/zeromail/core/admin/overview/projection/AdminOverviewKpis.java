package com.zeromail.core.admin.overview.projection;

import java.math.BigDecimal;
import java.util.Objects;

public record AdminOverviewKpis(
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

    public AdminOverviewKpis {
        Objects.requireNonNull(llmCostUsd, "llmCostUsd must not be null");
    }
}
