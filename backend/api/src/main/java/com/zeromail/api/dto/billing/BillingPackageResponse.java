package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.persistence.BillingPackageEntity;

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
