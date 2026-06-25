package com.zeromail.api.dto.admin.overview;

import com.zeromail.core.admin.overview.projection.AdminOverviewSuccessRate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"successRatePercent", "failureRatePercent"})
public record AdminOverviewSuccessRateResponse(
        double successRatePercent, double failureRatePercent) {

    public static AdminOverviewSuccessRateResponse from(AdminOverviewSuccessRate successRate) {
        return new AdminOverviewSuccessRateResponse(
                successRate.successRatePercent(), successRate.failureRatePercent());
    }
}
