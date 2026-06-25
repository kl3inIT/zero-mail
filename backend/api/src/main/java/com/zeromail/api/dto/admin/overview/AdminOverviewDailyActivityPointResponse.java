package com.zeromail.api.dto.admin.overview;

import com.zeromail.core.admin.overview.projection.AdminOverviewDailyActivityPoint;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        requiredProperties = {
            "date",
            "observedEmailCount",
            "triageActionCount",
            "failedTriageActionCount"
        })
public record AdminOverviewDailyActivityPointResponse(
        String date, int observedEmailCount, int triageActionCount, int failedTriageActionCount) {

    public static AdminOverviewDailyActivityPointResponse from(
            AdminOverviewDailyActivityPoint dailyActivityPoint) {
        return new AdminOverviewDailyActivityPointResponse(
                dailyActivityPoint.date(),
                dailyActivityPoint.observedEmailCount(),
                dailyActivityPoint.triageActionCount(),
                dailyActivityPoint.failedTriageActionCount());
    }
}
