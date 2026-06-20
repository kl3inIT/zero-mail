package com.zeromail.api.dto.admin.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.billing.projection.AdminBillingFeaturePermissionRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        requiredProperties = {
            "featureCode",
            "displayName",
            "category",
            "fixedCreditCost",
            "unitLabel",
            "sortOrder",
            "planPermissions"
        })
public record AdminBillingFeaturePermissionResponse(
        String featureCode,
        String displayName,
        String description,
        @Schema(allowableValues = {"TRIAGE", "COMPOSE", "RULES", "CLEANUP", "INTERNAL"})
                String category,
        int fixedCreditCost,
        String unitLabel,
        int sortOrder,
        List<AdminBillingPlanPermissionResponse> planPermissions) {

    public AdminBillingFeaturePermissionResponse {
        planPermissions = List.copyOf(planPermissions);
    }

    public static AdminBillingFeaturePermissionResponse from(
            AdminBillingFeaturePermissionRow featurePermission) {
        return new AdminBillingFeaturePermissionResponse(
                featurePermission.featureCode(),
                featurePermission.displayName(),
                featurePermission.description(),
                featurePermission.category(),
                featurePermission.fixedCreditCost(),
                featurePermission.unitLabel(),
                featurePermission.sortOrder(),
                featurePermission.planPermissions().stream()
                        .map(AdminBillingPlanPermissionResponse::from)
                        .toList());
    }
}
