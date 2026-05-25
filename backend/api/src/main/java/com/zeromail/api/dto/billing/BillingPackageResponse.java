package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.persistence.BillingPackageEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        requiredProperties = {
            "code",
            "name",
            "priceVnd",
            "creditAmount",
            "includedFeatures",
            "featured",
            "description",
            "displayOrder"
        })
public record BillingPackageResponse(
        String code,
        String name,
        long priceVnd,
        int creditAmount,
        List<String> includedFeatures,
        boolean featured,
        String description,
        int displayOrder) {

    public static BillingPackageResponse from(BillingPackageEntity billingPackage) {
        return new BillingPackageResponse(
                billingPackage.getCode(),
                billingPackage.getName(),
                billingPackage.getPriceVnd(),
                billingPackage.getCreditAmount(),
                List.of(billingPackage.getIncludedFeatures()),
                billingPackage.isFeatured(),
                billingPackage.getDescription(),
                billingPackage.getDisplayOrder());
    }
}
