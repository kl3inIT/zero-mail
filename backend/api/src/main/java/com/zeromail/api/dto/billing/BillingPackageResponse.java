package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.persistence.BillingPackageEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        requiredProperties = {
            "code",
            "name",
            "priceVnd",
            "creditAmount",
            "description",
            "displayOrder"
        })
public record BillingPackageResponse(
        String code,
        String name,
        long priceVnd,
        int creditAmount,
        String description,
        int displayOrder) {

    public static BillingPackageResponse from(BillingPackageEntity billingPackage) {
        return new BillingPackageResponse(
                billingPackage.getCode(),
                billingPackage.getName(),
                billingPackage.getPriceVnd(),
                billingPackage.getCreditAmount(),
                billingPackage.getDescription(),
                billingPackage.getDisplayOrder());
    }
}
