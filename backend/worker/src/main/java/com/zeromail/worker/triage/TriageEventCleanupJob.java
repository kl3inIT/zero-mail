package com.zeromail.worker.triage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("SqlResolve")
public class TriageEventCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TriageEventCleanupJob.class);
    private static final Duration COMPLETED_PUBLICATION_RETENTION = Duration.ofDays(7);

    private final CompletedEventPublications completedEventPublications;
    private final JdbcTemplate jdbcTemplate;
    private final Counter cleanupRunCounter;
    private final Counter skippedOutstandingCounter;

    public TriageEventCleanupJob(
            CompletedEventPublications completedEventPublications,
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry) {
        this.completedEventPublications = completedEventPublications;
        this.jdbcTemplate = jdbcTemplate;
        cleanupRunCounter =
                Counter.builder("zero_mail.triage.event_cleanup.run_total")
                        .description("Triage completed event publication cleanup job runs")
                        .register(meterRegistry);
        skippedOutstandingCounter =
                Counter.builder("zero_mail.triage.event_cleanup.skipped_outstanding_total")
                        .description(
                                "Outstanding triage event publications left untouched by cleanup")
                        .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "triageEventCleanup", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void deleteCompletedPublications() {
        int skippedOutstandingCount = countOutstandingPublications();
        completedEventPublications.deletePublicationsOlderThan(COMPLETED_PUBLICATION_RETENTION);
        cleanupRunCounter.increment();
        if (skippedOutstandingCount > 0) {
            skippedOutstandingCounter.increment(skippedOutstandingCount);
        }
        log.info(
                "event=triage_event_cleanup_run tenantId=system skippedOutstandingCount={}",
                skippedOutstandingCount);
    }

    private int countOutstandingPublications() {
        Integer outstandingPublicationCount =
                jdbcTemplate.queryForObject(
                        """
            SELECT COUNT(*)
              FROM event_publication
             WHERE completion_date IS NULL
                OR (status IS NOT NULL AND status != 'COMPLETED')
            """,
                        Integer.class);
        return outstandingPublicationCount == null ? 0 : outstandingPublicationCount;
    }
}
