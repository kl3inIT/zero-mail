package com.zeromail.api.dto.analytics;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum AnalyticsWindow implements IdentifiedEnum {
    SEVEN_DAYS("7d", Duration.ofDays(7)),
    THIRTY_DAYS("30d", Duration.ofDays(30)),
    NINETY_DAYS("90d", Duration.ofDays(90));

    private final String id;
    private final Duration duration;

    AnalyticsWindow(String id, Duration duration) {
        this.id = id;
        this.duration = duration;
    }

    @Override
    public String id() {
        return id;
    }

    public Duration duration() {
        return duration;
    }

    public static AnalyticsWindow fromId(String id) {
        return Stream.of(values())
                .filter(analyticsWindow -> analyticsWindow.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown AnalyticsWindow id: " + id));
    }
}
