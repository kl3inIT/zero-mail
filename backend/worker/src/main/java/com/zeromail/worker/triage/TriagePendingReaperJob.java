package com.zeromail.worker.triage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TriagePendingReaperJob {

    private static final Logger log = LoggerFactory.getLogger(TriagePendingReaperJob.class);
    private static final int BATCH_LIMIT = 500;

    private final TriagePendingReaperBatch triagePendingReaperBatch;
    private final Counter rowsProcessedCounter;

    public TriagePendingReaperJob(
            TriagePendingReaperBatch triagePendingReaperBatch, MeterRegistry meterRegistry) {
        this.triagePendingReaperBatch = triagePendingReaperBatch;
        rowsProcessedCounter =
                Counter.builder("zero_mail.triage.pending_reaper.rows_processed")
                        .description("Stuck PENDING triage audit rows reaped by the worker")
                        .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 300_000L)
    @SchedulerLock(name = "triagePendingReaper", lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
    public void scheduledReap() {
        reap();
    }

    public int reap() {
        int totalProcessed = 0;
        int processedCount;
        do {
            processedCount = triagePendingReaperBatch.reapStuckPendingOnce(BATCH_LIMIT);
            totalProcessed += processedCount;
        } while (processedCount == BATCH_LIMIT);

        if (totalProcessed > 0) {
            rowsProcessedCounter.increment(totalProcessed);
        }
        log.info("event=triage_pending_reaped tenantId=system totalProcessed={}", totalProcessed);
        return totalProcessed;
    }
}
