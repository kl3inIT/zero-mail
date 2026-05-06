package com.zeromail.worker.billing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.billing.model.IllegalLedgerStateException;
import com.zeromail.core.billing.model.ReservationId;
import com.zeromail.core.billing.persistence.CreditReservationRepository;
import com.zeromail.core.billing.persistence.StaleReservation;
import com.zeromail.core.billing.service.CreditLedger;
import com.zeromail.core.tenant.TenantContext;

/**
 * Transactional scan-and-release batch for the stale-reservation watchdog.
 *
 * <p>Lives in a separate bean from {@link CreditReserveWatchdog} on purpose: Spring's
 * {@code @Transactional} weaves through the proxy returned for the bean reference, so a
 * self-invoked method on the same instance bypasses the AOP interceptor and the database
 * connection runs in autocommit mode. By moving the transactional method onto its own
 * collaborator, the call from {@link CreditReserveWatchdog#tick()} crosses a proxy boundary and
 * the {@code FOR UPDATE SKIP LOCKED} row locks acquired by the scan stay held until every
 * {@link CreditLedger#release(ReservationId)} call in the loop returns.
 */
@Component
public class CreditReserveWatchdogBatch {

  private static final Logger log = LoggerFactory.getLogger(CreditReserveWatchdogBatch.class);

  private final CreditReservationRepository reservationRepository;
  private final CreditLedger creditLedger;

  public CreditReserveWatchdogBatch(
      CreditReservationRepository reservationRepository, CreditLedger creditLedger) {
    this.reservationRepository = reservationRepository;
    this.creditLedger = creditLedger;
  }

  /**
   * Scan and release stale pending reservations in a single transaction so the row locks acquired
   * by {@code FOR UPDATE SKIP LOCKED} are held for the duration of every {@link
   * CreditLedger#release(ReservationId)} call. Returns the count of reservations actually released
   * so the caller can update its metrics counter outside the transactional boundary.
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public int releaseStaleBatch(Duration staleThreshold, int batchLimit) {
    Instant olderThan = Instant.now().minus(staleThreshold);
    List<StaleReservation> staleReservations =
        reservationRepository.findStalePendingProjections(olderThan, batchLimit);
    int releasedCount = 0;
    for (StaleReservation staleReservation : staleReservations) {
      if (releaseOne(staleReservation)) {
        releasedCount++;
      }
    }
    return releasedCount;
  }

  private boolean releaseOne(StaleReservation staleReservation) {
    boolean[] released = new boolean[] {false};
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
                released[0] = true;
              } catch (IllegalLedgerStateException ledgerStateRace) {
                // A normal finalizer won the race between stale scan and release. Safe no-op.
              }
            });
    return released[0];
  }
}
