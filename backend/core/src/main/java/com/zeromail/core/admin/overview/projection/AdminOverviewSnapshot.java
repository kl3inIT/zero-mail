package com.zeromail.core.admin.overview.projection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AdminOverviewSnapshot(
        AdminOverviewRange range,
        AdminOverviewKpis kpis,
        AdminOverviewSuccessRate successRate,
        List<AdminOverviewDailyActivityPoint> dailyActivity,
        List<AdminOverviewActionDistribution> actionDistribution,
        List<AdminOverviewTopActivityTenant> topActivityTenants,
        List<AdminOverviewTopSpendTenant> topSpendTenants,
        List<AdminOverviewAlert> alerts,
        Instant snapshotAt) {

    public AdminOverviewSnapshot {
        Objects.requireNonNull(range, "range must not be null");
        Objects.requireNonNull(kpis, "kpis must not be null");
        Objects.requireNonNull(successRate, "successRate must not be null");
        dailyActivity = List.copyOf(dailyActivity);
        actionDistribution = List.copyOf(actionDistribution);
        topActivityTenants = List.copyOf(topActivityTenants);
        topSpendTenants = List.copyOf(topSpendTenants);
        alerts = List.copyOf(alerts);
        Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");
    }
}
