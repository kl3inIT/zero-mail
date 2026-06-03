package com.zeromail.core.cleanup.projection;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One bucket in the sender timeline bar chart (UNS-stats-01): {@code date} is the UTC calendar
 * day, {@code count} is the number of observed messages from the sender on that day. Empty days
 * inside the requested window are not returned — the FE fills gaps with 0 when rendering.
 */
public record SenderTimelineEntry(LocalDate date, long count) {

    public SenderTimelineEntry {
        Objects.requireNonNull(date, "date must not be null");
        if (count < 0L) {
            throw new IllegalArgumentException("count must be >= 0, was " + count);
        }
    }
}
