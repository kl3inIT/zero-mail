package com.zeromail.worker.waitlist;

import com.zeromail.core.waitlist.persistence.WaitlistEmailRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron scheduler that drains the waitlist invite queue. Runs every minute, ShedLock-guarded so only
 * one worker instance executes per tick across the cluster. Each tick claims up to {@link
 * #BATCH_SIZE} APPROVED rows whose {@code invite_next_attempt_at} is due and hands them to {@link
 * WaitlistInviteDispatchWorker#dispatchOne}.
 *
 * <p>Sequential dispatch is fine for v0 — Resend rate-limits at 2-10 req/sec, and {@link
 * #BATCH_SIZE} keeps the per-tick window well under that ceiling even before SDK-level throttling.
 * When throughput becomes a real concern, replace the for-loop with {@link
 * java.util.concurrent.StructuredTaskScope} fan-out (see {@code DigestDispatchScheduler}).
 */
@Component
public class WaitlistInviteDispatchScheduler {

    static final String DISPATCH_CRON = "0 * * * * *";
    static final String LOCK_NAME = "waitlistInviteDispatcher";
    static final int BATCH_SIZE = 50;

    private static final Logger LOG =
            LoggerFactory.getLogger(WaitlistInviteDispatchScheduler.class);

    private final WaitlistEmailRepository waitlistEmailRepository;
    private final WaitlistInviteDispatchWorker waitlistInviteDispatchWorker;
    private final Clock clock;

    public WaitlistInviteDispatchScheduler(
            WaitlistEmailRepository waitlistEmailRepository,
            WaitlistInviteDispatchWorker waitlistInviteDispatchWorker,
            Clock clock) {
        this.waitlistEmailRepository =
                Objects.requireNonNull(
                        waitlistEmailRepository, "waitlistEmailRepository must not be null");
        this.waitlistInviteDispatchWorker =
                Objects.requireNonNull(
                        waitlistInviteDispatchWorker,
                        "waitlistInviteDispatchWorker must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(cron = DISPATCH_CRON)
    @SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void dispatchPendingInvites() {
        LockAssert.assertLocked();
        Instant referenceInstant = clock.instant();
        List<UUID> dueIds = waitlistEmailRepository.findDueInviteIds(referenceInstant, BATCH_SIZE);
        if (dueIds.isEmpty()) {
            return;
        }
        LOG.info("event=waitlist.invite.batch_start dueCount={}", dueIds.size());
        for (UUID waitlistId : dueIds) {
            try {
                waitlistInviteDispatchWorker.dispatchOne(waitlistId, referenceInstant);
            } catch (RuntimeException dispatchFailure) {
                // Failure isolation: one row failing must not abort the rest of the batch.
                LOG.warn(
                        "event=waitlist.invite.row_failed waitlistId={} failureType={}",
                        waitlistId,
                        dispatchFailure.getClass().getSimpleName(),
                        dispatchFailure);
            }
        }
        LOG.info("event=waitlist.invite.batch_done processed={}", dueIds.size());
    }
}
