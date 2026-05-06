package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;

public interface BillingTopupIntentRepository
        extends JpaRepository<BillingTopupIntentEntity, UUID>, BillingTopupIntentTenantLookupFragment {

    Optional<BillingTopupIntentEntity> findByCode(String code);

    int countByTenantIdAndStatus(UUID tenantId, BillingTopupIntentStatus status);

    Optional<BillingTopupIntentEntity> findFirstByTenantIdAndStatusOrderByCreatedAtAsc(
            UUID tenantId, BillingTopupIntentStatus status);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE billing_topup_intent
               SET status = 'EXPIRED',
                   updated_at = CURRENT_TIMESTAMP
             WHERE status = 'PENDING'
               AND expires_at < :now
            """, nativeQuery = true)
    int expireStale(@Param("now") Instant now);

    /**
     * Atomic conditional state transition for the SePay webhook idempotency contract. Two
     * concurrent webhook deliveries for the same intent both pass the pre-transaction read; only
     * the call that updates exactly one row may write the {@code TOPUP} ledger entry. The losing
     * call sees {@code 0} rows affected and must treat it as a replay ack rather than a 409.
     *
     * <p>Bypasses Hibernate's optimistic lock and managed-entity flush, so the version column is
     * bumped explicitly here; Spring Data's audit listener does not fire on bulk SQL updates, so
     * {@code updated_at} is set in the query as well.
     */
    @Modifying
    @Query(value = """
            UPDATE billing_topup_intent
               SET status = 'PAID',
                   paid_at = CURRENT_TIMESTAMP,
                   sepay_transaction_id = :sepayTransactionId,
                   updated_at = CURRENT_TIMESTAMP,
                   version = version + 1
             WHERE id = :intentId
               AND status = 'PENDING'
            """, nativeQuery = true)
    int markPaidIfPending(
            @Param("intentId") UUID intentId,
            @Param("sepayTransactionId") String sepayTransactionId);
}
