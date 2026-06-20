package com.zeromail.core.admin.overview.projection;

import java.util.Objects;
import java.util.UUID;

public record AdminOverviewTopActivityTenant(
        UUID tenantId,
        String tenantDisplayName,
        String ownerEmail,
        String primaryEmail,
        int observedEmailCount,
        int triageActionCount,
        int failedTriageActionCount,
        int outboundActionCount,
        int blockedOutboundActionCount,
        double failureRatePercent) {

    public AdminOverviewTopActivityTenant {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(tenantDisplayName, "tenantDisplayName must not be null");
    }
}
