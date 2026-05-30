package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.projection.BillingPlanCatalogView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"currentPlanCode", "plans"})
public record BillingPlanListResponse(
        @Schema(
                        description =
                                "Tenant's currently-active plan code. Falls back to FREE when no active paid plan period exists.",
                        allowableValues = {"FREE", "PLUS", "PRO"})
                String currentPlanCode,
        List<BillingPlanResponse> plans) {

    public static BillingPlanListResponse from(BillingPlanCatalogView catalog) {
        return new BillingPlanListResponse(
                catalog.currentPlanCode(),
                catalog.plans().stream().map(BillingPlanResponse::from).toList());
    }
}
