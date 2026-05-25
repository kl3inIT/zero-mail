package com.zeromail.api.dto.admin.billing;

import com.zeromail.core.admin.billing.projection.BillingPackageAdminRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "id",
            "code",
            "name",
            "priceVnd",
            "active",
            "displayOrder",
            "purchaseCount",
            "pendingIntentCount",
            "totalRevenueVnd"
        })
public record BillingPackageAdminResponse(
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

    public static BillingPackageAdminResponse from(BillingPackageAdminRow row) {
        return new BillingPackageAdminResponse(
                row.id(),
                row.code(),
                row.name(),
                row.priceVnd(),
                row.description(),
                row.active(),
                row.displayOrder(),
                row.createdAt(),
                row.updatedAt(),
                row.purchaseCount(),
                row.pendingIntentCount(),
                row.totalRevenueVnd(),
                row.lastPurchasedAt());
    }
}
