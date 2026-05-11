package com.zeromail.worker.billing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases orphaned credit reservations after the normal reserve/settle/release path has failed to
 * finalize them.
 *
 * <p>Delegation to {@link CreditReserveWatchdogBatch} is deliberate: if the transactional
 * scan-and-release method lived on this class and was invoked via {@code tick()}, Spring's proxy
 * would not fire on the self-invocation and the {@code FOR UPDATE SKIP LOCKED} row locks would be
 * released the instant the JDBC autocommit query returned, before the loop could call {@link
 * com.zeromail.core.billing.service.CreditLedger#release}. The collaborator boundary is what
 * guarantees the locks stay held across the loop.
 */
@Component
public class CreditReserveWatchdog {

    private static final Logger log = LoggerFactory.getLogger(CreditReserveWatchdog.class);
    private static final int BATCH_LIMIT = 100;
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private final CreditReserveWatchdogBatch batch;
    private final Counter releasedTotal;

    public CreditReserveWatchdog(CreditReserveWatchdogBatch batch, MeterRegistry meterRegistry) {
        this.batch = batch;
        releasedTotal =
                Counter.builder("zero_mail.billing.watchdog.released_total")
                        .description("Stale credit reservations released by the worker watchdog")
                        .register(meterRegistry);
    }

    @Scheduled(fixedRate = 60_000L)
    @SchedulerLock(name = "creditReserveWatchdog", lockAtLeastFor = "PT30S", lockAtMostFor = "PT2M")
    public void scheduledTick() {
        tick();
    }

    public void tick() {
        int releasedCount = batch.releaseStaleBatch(STALE_THRESHOLD, BATCH_LIMIT);
        if (releasedCount > 0) {
            releasedTotal.increment(releasedCount);
            log.info("event=credit_reserve_watchdog_batch releasedCount={}", releasedCount);
        }
    }
}
