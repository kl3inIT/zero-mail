package com.zeromail.core.admin.overview.projection;

import java.util.Objects;

public record AdminOverviewDailyActivityPoint(
        String date, int observedEmailCount, int triageActionCount, int failedTriageActionCount) {

    public AdminOverviewDailyActivityPoint {
        Objects.requireNonNull(date, "date must not be null");
    }
}
