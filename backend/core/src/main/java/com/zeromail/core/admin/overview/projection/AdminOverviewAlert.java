package com.zeromail.core.admin.overview.projection;

import java.util.Objects;

public record AdminOverviewAlert(
        String key, String severity, String title, String detail, int count, String timeLabel) {

    public AdminOverviewAlert {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(detail, "detail must not be null");
        Objects.requireNonNull(timeLabel, "timeLabel must not be null");
    }
}
