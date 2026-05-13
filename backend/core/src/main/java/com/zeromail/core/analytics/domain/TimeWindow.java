package com.zeromail.core.analytics.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record TimeWindow(Instant startInclusive, Instant endExclusive) {

    public TimeWindow {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endExclusive, "endExclusive must not be null");
        if (!endExclusive.isAfter(startInclusive)) {
            throw new IllegalArgumentException("endExclusive must be after startInclusive");
        }
    }

    public static TimeWindow endingAt(Instant endExclusive, Duration duration) {
        Objects.requireNonNull(endExclusive, "endExclusive must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        return new TimeWindow(endExclusive.minus(duration), endExclusive);
    }

    public static TimeWindow between(Instant startInclusive, Instant endExclusive) {
        return new TimeWindow(startInclusive, endExclusive);
    }
}
