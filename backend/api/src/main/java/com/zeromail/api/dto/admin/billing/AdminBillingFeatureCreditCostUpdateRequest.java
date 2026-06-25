package com.zeromail.api.dto.admin.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"fixedCreditCost"})
public record AdminBillingFeatureCreditCostUpdateRequest(@NotNull @Min(0) Integer fixedCreditCost) {

    public int fixedCreditCostValue() {
        return fixedCreditCost;
    }
}
