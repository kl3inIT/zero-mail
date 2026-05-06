package com.zeromail.core.billing.service;

import java.util.UUID;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.billing.model.CreditBalance;
import com.zeromail.core.billing.model.IllegalLedgerStateException;
import com.zeromail.core.billing.model.InsufficientCreditsException;
import com.zeromail.core.billing.model.ReservationId;

/**
 * Prepaid credit ledger: the cross-phase contract that Phase 2C ({@code core.llm.LlmGateway})
 * imports verbatim. Callers depend on this interface only; {@link CreditLedgerService} owns
 * the package-private implementation.
 *
 * <h3>Reserve / settle / release lifecycle (D-D1)</h3>
 *
 * <p>Phase 2C calls {@link #settle(ReservationId)} on success and
 * {@link #release(ReservationId)} on the exception path:
 *
 * <pre>{@code
 * ReservationId reservationId = creditLedger.reserve(tenantId, CallSite.TRIAGE);
 * try {
 *     ChatResponse response = chatClient.call(prompt);
 *     creditLedger.settle(reservationId);
 *     return response;
 * } catch (Exception failure) {
 *     creditLedger.release(reservationId);
 *     throw failure;
 * }
 * }</pre>
 *
 * <p>The watchdog ({@code backend/worker.billing.CreditReserveWatchdog}) is the safety net
 * for crashes between {@code reserve} and {@code settle}/{@code release}, not the
 * steady-state finalizer. Reservations older than 5 minutes with no SETTLE/RELEASE journal
 * entry are auto-released by the watchdog.
 *
 * <h3>Idempotency (D-D2 + D-D3)</h3>
 *
 * <ul>
 *   <li>{@code settle} called twice on the same reservation: second call is a no-op.</li>
 *   <li>{@code release} called twice on the same reservation: second call is a no-op.</li>
 *   <li>{@code release} after {@code settle}, or {@code settle} after {@code release}, throws
 *       {@link IllegalLedgerStateException}.</li>
 * </ul>
 *
 * <h3>Concurrency (D-A1 + D-A2)</h3>
 *
 * <p>{@link #reserve(UUID, CallSite)} runs in {@code Propagation.REQUIRES_NEW} so an outer
 * transaction failure cannot roll back a successful reserve. The implementation acquires a
 * per-tenant Postgres advisory lock ({@code pg_advisory_xact_lock(hashtext(tenantId))})
 * inside the same transaction as the SUM-balance check and RESERVE insert.
 *
 * <p>{@link #settle(ReservationId)} and {@link #release(ReservationId)} run in
 * {@code Propagation.REQUIRED}, giving the caller control over finalization atomicity.
 *
 * <h3>BYOK exemption (BILL-07)</h3>
 *
 * <p><b>BYOK traffic bypasses the ledger entirely.</b> Phase 2C's {@code LlmGateway} MUST
 * check the {@code tenant_byok_credentials} table before calling {@link #reserve} and skip
 * this method when a BYOK row exists for the tenant. The {@link CallSite} enum has no BYOK
 * member because BYOK traffic does not enter this interface.
 *
 * <h3>Privacy invariants</h3>
 *
 * <ul>
 *   <li>{@link InsufficientCreditsException} carries no balance number.</li>
 *   <li>Implementation logs use the {@code event=opaque tenantId={}} format.</li>
 * </ul>
 *
 * @see CallSite
 * @see ReservationId
 * @see CreditBalance
 * @see InsufficientCreditsException
 * @see IllegalLedgerStateException
 */
public interface CreditLedger {

    /**
     * Atomically reserve {@code callSite.cost()} credits against the tenant's available
     * balance.
     *
     * @return a handle for the subsequent {@link #settle(ReservationId)} or
     *         {@link #release(ReservationId)} call.
     * @throws InsufficientCreditsException if {@code availableCredits < callSite.cost()}.
     */
    ReservationId reserve(UUID tenantId, CallSite callSite);

    /**
     * Finalize a reservation as consumed. Idempotent on repeat call; throws
     * {@link IllegalLedgerStateException} if the reservation is already RELEASED.
     */
    void settle(ReservationId reservationId);

    /**
     * Reverse a reservation back to available balance. Idempotent on repeat call; throws
     * {@link IllegalLedgerStateException} if the reservation is already SETTLED.
     */
    void release(ReservationId reservationId);

    /**
     * Read the tenant's current available and held credit balance.
     */
    CreditBalance balance(UUID tenantId);
}
