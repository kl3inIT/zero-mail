package com.zeromail.api.dto.admin.billing;

import com.zeromail.core.admin.billing.projection.AdminBillingPlanRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "planId",
            "code",
            "displayName",
            "tierRank",
            "billingCycle",
            "currency",
            "priceVnd",
            "monthlyCreditAllowance",
            "active",
            "sortOrder"
        })
public record AdminBillingPlanResponse(
        UUID planId,
        String code,
        String displayName,
        short tierRank,
        @Schema(allowableValues = {"NONE", "MONTH"}) String billingCycle,
        String currency,
        long priceVnd,
        int monthlyCreditAllowance,
        boolean active,
        int sortOrder) {

    public static AdminBillingPlanResponse from(AdminBillingPlanRow plan) {
        return new AdminBillingPlanResponse(
                plan.planId(),
                plan.code(),
                plan.displayName(),
                plan.tierRank(),
                plan.billingCycle(),
                plan.currency(),
                plan.priceVnd(),
                plan.monthlyCreditAllowance(),
                plan.active(),
                plan.sortOrder());
    }
}
