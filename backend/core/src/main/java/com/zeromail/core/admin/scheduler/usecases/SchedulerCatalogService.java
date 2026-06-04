package com.zeromail.core.admin.scheduler.usecases;

import com.zeromail.core.admin.scheduler.domain.SchedulerCatalog;
import com.zeromail.core.admin.scheduler.domain.SchedulerDescriptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * Read-only view of the {@link SchedulerCatalog}: returns each scheduler descriptor with its next
 * cron fire time computed (UTC). Pure read; no scheduler is triggered or mutated here.
 */
@Service
public class SchedulerCatalogService {

    private final Clock clock;

    public SchedulerCatalogService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public List<SchedulerStatus> listSchedulers() {
        return SchedulerCatalog.ALL.stream()
                .map(descriptor -> new SchedulerStatus(descriptor, nextRunAt(descriptor)))
                .toList();
    }

    private Instant nextRunAt(SchedulerDescriptor descriptor) {
        if (!descriptor.isCron()
                || !CronExpression.isValidExpression(descriptor.cronExpression())) {
            return null;
        }
        ZonedDateTime next =
                CronExpression.parse(descriptor.cronExpression())
                        .next(ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return next == null ? null : next.toInstant();
    }
}
