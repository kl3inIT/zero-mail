package com.zeromail.api.dto.admin.overview;

import com.zeromail.core.admin.overview.projection.AdminOverviewAlert;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"key", "severity", "title", "detail", "count", "timeLabel"})
public record AdminOverviewAlertResponse(
        String key, String severity, String title, String detail, int count, String timeLabel) {

    public static AdminOverviewAlertResponse from(AdminOverviewAlert alert) {
        return new AdminOverviewAlertResponse(
                alert.key(),
                alert.severity(),
                alert.title(),
                alert.detail(),
                alert.count(),
                alert.timeLabel());
    }
}
