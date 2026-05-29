package com.zeromail.api.dto.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.billing.projection.BillingPlanView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        requiredProperties = {
            "code",
            "displayName",
            "tierRank",
            "billingCycle",
            "currency",
            "priceVnd",
            "monthlyCreditAllowance",
            "sortOrder",
            "features"
        })
public record BillingPlanResponse(
        @Schema(allowableValues = {"FREE", "PLUS", "PRO"}) String code,
        String displayName,
        int tierRank,
        @Schema(allowableValues = {"NONE", "MONTH"}) String billingCycle,
        String currency,
        long priceVnd,
        int monthlyCreditAllowance,
        int sortOrder,
        @Schema(
                        description =
                                "Enabled features for this plan, sorted by feature catalog sort order")
                List<PlanFeatureSummaryResponse> features) {

    public static BillingPlanResponse from(BillingPlanView view) {
        return new BillingPlanResponse(
                view.code(),
                view.displayName(),
                view.tierRank(),
                view.billingCycle(),
                view.currency(),
                view.priceVnd(),
                view.monthlyCreditAllowance(),
                view.sortOrder(),
                view.features().stream().map(PlanFeatureSummaryResponse::from).toList());
    }
}
