package com.zeromail.core.admin.overview.projection;

import com.zeromail.core.admin.overview.exception.AdminOverviewInvalidRangeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record AdminOverviewQuery(Instant from, Instant to) {

    public static final Duration MAX_RANGE = Duration.ofDays(90);

    public AdminOverviewQuery {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!to.isAfter(from)) {
            throw new AdminOverviewInvalidRangeException("to must be after from");
        }
        Duration range = Duration.between(from, to);
        if (range.compareTo(MAX_RANGE) > 0) {
            throw new AdminOverviewInvalidRangeException("range must not exceed 90 days");
        }
    }
}
