package com.zeromail.core.admin.scheduler.usecases;

import com.zeromail.core.admin.scheduler.domain.SchedulerDescriptor;
import java.time.Instant;

/**
 * A scheduler descriptor plus its computed next fire time. {@code nextRunAt} is non-null only for
 * cron-based schedulers (fixed-delay/rate loops have no meaningful single "next" instant). Live
 * last-run/status is deferred to scheduler phase 2.
 */
public record SchedulerStatus(SchedulerDescriptor descriptor, Instant nextRunAt) {}
