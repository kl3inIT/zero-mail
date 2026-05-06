package com.zeromail.worker.billing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zeromail.core.billing.model.IllegalLedgerStateException;
import com.zeromail.core.billing.model.ReservationId;
import com.zeromail.core.billing.persistence.CreditReservationRepository;
import com.zeromail.core.billing.persistence.StaleReservation;
import com.zeromail.core.billing.service.CreditLedger;
import com.zeromail.core.tenant.TenantContext;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Releases orphaned credit reservations after the normal reserve/settle/release path has failed to
 * finalize them.
 */
@Component
public class CreditReserveWatchdog {

  private static final Logger log = LoggerFactory.getLogger(CreditReserveWatchdog.class);
  private static final int BATCH_LIMIT = 100;
  private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

  private final CreditReservationRepository reservationRepository;
  private final CreditLedger creditLedger;
  private final Counter releasedTotal;

  public CreditReserveWatchdog(
      CreditReservationRepository reservationRepository,
      CreditLedger creditLedger,
      MeterRegistry meterRegistry) {
    this.reservationRepository = reservationRepository;
    this.creditLedger = creditLedger;
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
    Instant olderThan = Instant.now().minus(STALE_THRESHOLD);
    List<StaleReservation> staleReservations =
        reservationRepository.findStalePendingProjections(olderThan, BATCH_LIMIT);
    for (StaleReservation staleReservation : staleReservations) {
      releaseOne(staleReservation);
    }
  }

  private void releaseOne(StaleReservation staleReservation) {
    ScopedValue.where(TenantContext.TENANT, staleReservation.tenantId().toString())
        .run(
            () -> {
              try {
                creditLedger.release(new ReservationId(staleReservation.id()));
                long ageSeconds =
                    Duration.between(staleReservation.createdAt().toInstant(), Instant.now())
                        .toSeconds();
                log.info(
                    "event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}",
                    staleReservation.tenantId(),
                    staleReservation.id(),
                    ageSeconds);
                releasedTotal.increment();
              } catch (IllegalLedgerStateException ledgerStateRace) {
                // A normal finalizer won the race between stale scan and release. Safe no-op.
              }
            });
  }
}
