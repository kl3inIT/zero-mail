package com.zeromail.worker.billing;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic-claim batch for the stale-reservation watchdog.
 *
 * <p>Lives in a separate bean from {@link CreditReserveWatchdog} on purpose: Spring's
 * {@code @Transactional} weaves through the proxy returned for the bean reference, so a
 * self-invoked method on the same instance bypasses the AOP interceptor and the database
 * connection runs in autocommit mode. By moving the transactional method onto its own
 * collaborator, the call from {@link CreditReserveWatchdog#tick()} crosses a proxy boundary and
 * the {@code FOR UPDATE SKIP LOCKED} row locks acquired by the scan stay held until the
 * accompanying ledger writes finish.
 *
 * <p>Implementation note: this batch deliberately sidesteps Hibernate for the write path. Hibernate
 * resolves the current tenant identifier once per persistence-context lifetime and rejects mid-tx
 * tenant rebinds (see {@code ScopedValueTenantResolver.validateExistingCurrentSessions=true}). The
 * watchdog must process reservations across many tenants in a single batch, so it cannot share a
 * tenant-bound Hibernate session with {@code CreditLedger.release(...)}. The atomic CTE-based
 * UPDATE here performs the equivalent state transition + RELEASE ledger insert in pure SQL,
 * preserving idempotency via the existing {@code uq_credit_ledger_entry_ref_kind} unique
 * constraint.
 */
@Component
public class CreditReserveWatchdogBatch {

  private static final Logger log = LoggerFactory.getLogger(CreditReserveWatchdogBatch.class);

  private final JdbcTemplate jdbcTemplate;

  public CreditReserveWatchdogBatch(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Atomically claim and release stale pending reservations in a single transaction. The CTE
   * acquires {@code FOR UPDATE SKIP LOCKED} row locks for stale rows, flips their status to
   * {@code RELEASED} in the same statement, and the wrapping transaction holds those locks
   * until the matching {@code RELEASE} ledger entries are inserted on the same connection.
   *
   * <p>The RELEASE ledger insert uses {@code ON CONFLICT DO NOTHING} against
   * {@code uq_credit_ledger_entry_ref_kind}, so concurrent watchdog invocations or a re-run
   * after a partial commit are both safe — the first writer wins and later writers no-op.
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public int releaseStaleBatch(Duration staleThreshold, int batchLimit) {
    Instant olderThan = Instant.now().minus(staleThreshold);
    List<ClaimedReservation> claimed =
        jdbcTemplate.query(
            """
            WITH stale_pending AS (
              SELECT id
                FROM credit_reservation
               WHERE status = 'PENDING' AND created_at < ?
               ORDER BY created_at
               LIMIT ?
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE credit_reservation
               SET status = 'RELEASED',
                   finalized_at = CURRENT_TIMESTAMP,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE id IN (SELECT id FROM stale_pending)
            RETURNING id, tenant_id, amount_credits, created_at
            """,
            (resultSet, rowIndex) ->
                new ClaimedReservation(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("tenant_id", UUID.class),
                    resultSet.getInt("amount_credits"),
                    resultSet.getTimestamp("created_at").toInstant()),
            java.sql.Timestamp.from(olderThan),
            batchLimit);

    if (claimed.isEmpty()) {
      return 0;
    }

    int releasedCount = 0;
    for (ClaimedReservation claimedReservation : claimed) {
      int ledgerRowsInserted =
          jdbcTemplate.update(
              """
              INSERT INTO credit_ledger_entry
                  (id, tenant_id, kind, amount_credits, ref_type, ref_id)
              VALUES (?, ?, 'RELEASE', ?, 'RESERVATION', ?)
              ON CONFLICT ON CONSTRAINT uq_credit_ledger_entry_ref_kind DO NOTHING
              """,
              UUID.randomUUID(),
              claimedReservation.tenantId(),
              claimedReservation.amountCredits(),
              claimedReservation.id().toString());
      if (ledgerRowsInserted == 1) {
        long ageSeconds =
            Duration.between(claimedReservation.createdAt(), Instant.now()).toSeconds();
        log.info(
            "event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}",
            claimedReservation.tenantId(),
            claimedReservation.id(),
            ageSeconds);
        releasedCount++;
      } else {
        // The reservation row state has been advanced (we did the UPDATE above), but the
        // RELEASE ledger entry was already present from a prior partial run. Treat as
        // already-released; do not double-count.
        log.info(
            "event=watchdog_release_ledger_already_present tenantId={} reservationId={}",
            claimedReservation.tenantId(),
            claimedReservation.id());
      }
    }
    return releasedCount;
  }

  private record ClaimedReservation(
      UUID id, UUID tenantId, int amountCredits, Instant createdAt) {}
}
