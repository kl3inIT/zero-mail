package com.zeromail.api.dto.admin.overview;

import com.zeromail.core.admin.overview.projection.AdminOverviewSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
        requiredProperties = {
            "range",
            "kpis",
            "successRate",
            "dailyActivity",
            "actionDistribution",
            "topActivityTenants",
            "topSpendTenants",
            "alerts",
            "snapshotAt"
        })
public record AdminOverviewResponse(
        AdminOverviewRangeResponse range,
        AdminOverviewKpisResponse kpis,
        AdminOverviewSuccessRateResponse successRate,
        List<AdminOverviewDailyActivityPointResponse> dailyActivity,
        List<AdminOverviewActionDistributionResponse> actionDistribution,
        List<AdminOverviewTopActivityTenantResponse> topActivityTenants,
        List<AdminOverviewTopSpendTenantResponse> topSpendTenants,
        List<AdminOverviewAlertResponse> alerts,
        Instant snapshotAt) {

    public AdminOverviewResponse {
        dailyActivity = List.copyOf(dailyActivity);
        actionDistribution = List.copyOf(actionDistribution);
        topActivityTenants = List.copyOf(topActivityTenants);
        topSpendTenants = List.copyOf(topSpendTenants);
        alerts = List.copyOf(alerts);
    }

    public static AdminOverviewResponse from(AdminOverviewSnapshot snapshot) {
        return new AdminOverviewResponse(
                AdminOverviewRangeResponse.from(snapshot.range()),
                AdminOverviewKpisResponse.from(snapshot.kpis()),
                AdminOverviewSuccessRateResponse.from(snapshot.successRate()),
                snapshot.dailyActivity().stream()
                        .map(AdminOverviewDailyActivityPointResponse::from)
                        .toList(),
                snapshot.actionDistribution().stream()
                        .map(AdminOverviewActionDistributionResponse::from)
                        .toList(),
                snapshot.topActivityTenants().stream()
                        .map(AdminOverviewTopActivityTenantResponse::from)
                        .toList(),
                snapshot.topSpendTenants().stream()
                        .map(AdminOverviewTopSpendTenantResponse::from)
                        .toList(),
                snapshot.alerts().stream().map(AdminOverviewAlertResponse::from).toList(),
                snapshot.snapshotAt());
    }
}
