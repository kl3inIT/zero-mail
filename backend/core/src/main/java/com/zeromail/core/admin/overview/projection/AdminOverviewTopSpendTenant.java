package com.zeromail.core.admin.overview.projection;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record AdminOverviewTopSpendTenant(
        UUID tenantId,
        String tenantDisplayName,
        String ownerEmail,
        String primaryEmail,
        int llmCallCount,
        long chargedCredits,
        BigDecimal totalCostUsd) {

    public AdminOverviewTopSpendTenant {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(tenantDisplayName, "tenantDisplayName must not be null");
        Objects.requireNonNull(totalCostUsd, "totalCostUsd must not be null");
    }
}
