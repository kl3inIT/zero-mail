package com.zeromail.core.admin.billing.projection;

import com.zeromail.core.billing.persistence.BillingPackageEntity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BillingPackageAdminRow(
        UUID id,
        String code,
        String name,
        long priceVnd,
        String description,
        boolean active,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt,
        long purchaseCount,
        long pendingIntentCount,
        long totalRevenueVnd,
        Instant lastPurchasedAt) {

    public static BillingPackageAdminRow from(
            BillingPackageEntity billingPackage, BillingPackageAdminStats stats) {
        BillingPackageEntity packageEntity =
                Objects.requireNonNull(billingPackage, "billingPackage must not be null");
        BillingPackageAdminStats packageStats =
                stats == null ? BillingPackageAdminStats.empty(packageEntity.getId()) : stats;
        return new BillingPackageAdminRow(
                packageEntity.getId(),
                packageEntity.getCode(),
                packageEntity.getName(),
                packageEntity.getPriceVnd(),
                packageEntity.getDescription(),
                packageEntity.isActive(),
                packageEntity.getDisplayOrder(),
                packageEntity.getCreatedAt(),
                packageEntity.getUpdatedAt(),
                packageStats.purchaseCount(),
                packageStats.pendingIntentCount(),
                packageStats.totalRevenueVnd(),
                packageStats.lastPurchasedAt());
    }
}
