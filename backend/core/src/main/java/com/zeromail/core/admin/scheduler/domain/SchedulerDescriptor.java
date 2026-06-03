package com.zeromail.core.admin.scheduler.domain;

import java.util.Objects;

/**
 * Static description of one background scheduler. {@code cronExpression} is a Spring 6-field cron
 * string when the schedule is cron-based (so the next fire time can be computed), or {@code null}
 * for fixed-delay / fixed-rate loops where {@code scheduleText} carries the human description.
 */
public record SchedulerDescriptor(
        String key,
        String displayName,
        String scheduleText,
        String cronExpression,
        SchedulerProcess process,
        String category,
        String description) {

    public SchedulerDescriptor {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(scheduleText, "scheduleText must not be null");
        Objects.requireNonNull(process, "process must not be null");
        Objects.requireNonNull(category, "category must not be null");
    }

    public boolean isCron() {
        return cronExpression != null && !cronExpression.isBlank();
    }
}
