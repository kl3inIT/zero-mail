package com.zeromail.api.dto.admin.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"enabled"})
public record AdminBillingFeaturePermissionUpdateRequest(@NotNull Boolean enabled) {

    public boolean enabledValue() {
        return Boolean.TRUE.equals(enabled);
    }
}
