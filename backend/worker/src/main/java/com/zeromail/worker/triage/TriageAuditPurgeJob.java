package com.zeromail.worker.triage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TriageAuditPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TriageAuditPurgeJob.class);
    private static final int BATCH_LIMIT = 1_000;

    private final TriageAuditPurgeBatch triageAuditPurgeBatch;
    private final Counter purgedRowsCounter;

    public TriageAuditPurgeJob(
            TriageAuditPurgeBatch triageAuditPurgeBatch, MeterRegistry meterRegistry) {
        this.triageAuditPurgeBatch = triageAuditPurgeBatch;
        purgedRowsCounter =
                Counter.builder("zero_mail.triage.audit_purge.rows_deleted_total")
                        .description("Triage audit rows deleted by the retention purge")
                        .register(meterRegistry);
    }

    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "triageAuditPurge", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void scheduledPurge() {
        purge();
    }

    public int purge() {
        int totalDeleted = 0;
        int deletedCount;
        do {
            deletedCount = triageAuditPurgeBatch.purgeExpiredOnce(BATCH_LIMIT);
            totalDeleted += deletedCount;
        } while (deletedCount == BATCH_LIMIT);

        if (totalDeleted > 0) {
            purgedRowsCounter.increment(totalDeleted);
        }
        log.info("event=triage_audit_purged tenantId=system totalDeleted={}", totalDeleted);
        return totalDeleted;
    }
}
