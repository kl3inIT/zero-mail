package com.zeromail.core.admin.billing.projection;

import java.time.Instant;
import java.util.UUID;

public record BillingPackageAdminStats(
        UUID packageId,
        long purchaseCount,
        long pendingIntentCount,
        long totalRevenueVnd,
        Instant lastPurchasedAt) {

    public static BillingPackageAdminStats empty(UUID packageId) {
        return new BillingPackageAdminStats(packageId, 0, 0, 0, null);
    }
}
