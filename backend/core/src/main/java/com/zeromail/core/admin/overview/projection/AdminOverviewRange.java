package com.zeromail.core.admin.overview.projection;

import java.time.Instant;
import java.util.Objects;

public record AdminOverviewRange(Instant from, Instant to) {

    public AdminOverviewRange {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
    }
}
