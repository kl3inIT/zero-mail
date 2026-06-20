package com.zeromail.api.dto.admin.billing;

import com.zeromail.core.admin.billing.projection.AdminBillingPlanPermission;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"planCode", "enabled"})
public record AdminBillingPlanPermissionResponse(String planCode, boolean enabled) {

    public static AdminBillingPlanPermissionResponse from(
            AdminBillingPlanPermission planPermission) {
        return new AdminBillingPlanPermissionResponse(
                planPermission.planCode(), planPermission.enabled());
    }
}
