package com.zeromail.worker.admin;

import com.zeromail.core.admin.audit.persistence.AdminReadEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminReadEventPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AdminReadEventPurgeJob.class);
    private static final Duration RETENTION = Duration.ofDays(30);

    private final AdminReadEventRepository adminReadEventRepository;
    private final Clock clock;
    private final Counter purgedRowsCounter;

    public AdminReadEventPurgeJob(
            AdminReadEventRepository adminReadEventRepository,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.adminReadEventRepository = adminReadEventRepository;
        this.clock = clock;
        purgedRowsCounter =
                Counter.builder("zero_mail.admin.read_event_purge.rows_deleted_total")
                        .description("Admin read audit rows deleted after the retention window")
                        .register(meterRegistry);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "adminReadEventPurge", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void scheduledPurge() {
        purgeOnce();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public int purgeOnce() {
        Instant retentionCutoff = clock.instant().minus(RETENTION);
        int deletedRows = adminReadEventRepository.deleteOlderThan(retentionCutoff);
        if (deletedRows > 0) {
            purgedRowsCounter.increment(deletedRows);
        }
        log.info("event=admin_read_event_purged tenantId=system deletedRows={}", deletedRows);
        return deletedRows;
    }
}
