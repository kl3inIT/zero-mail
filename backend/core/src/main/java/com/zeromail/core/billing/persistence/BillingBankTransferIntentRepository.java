package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingBankTransferIntentRepository
        extends JpaRepository<BillingBankTransferIntentEntity, UUID> {

    Optional<BillingBankTransferIntentEntity> findByCode(String code);

    Optional<BillingBankTransferIntentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            value =
                    """
                            SELECT *
                            FROM billing_bank_transfer_intent
                            WHERE tenant_id = :tenantId
                              AND plan_id = :planId
                              AND provider = :provider
                              AND status = :status
                              AND expires_at > :now
                            ORDER BY created_at DESC
                            LIMIT 1
                            """,
            nativeQuery = true)
    Optional<BillingBankTransferIntentEntity> findReusableBankTransferIntent(
            @Param("tenantId") UUID tenantId,
            @Param("planId") UUID planId,
            @Param("provider") String provider,
            @Param("status") String status,
            @Param("now") Instant now);

    @Modifying
    @Query(
            value =
                    """
                            UPDATE billing_bank_transfer_intent
                               SET status = 'PAID',
                                   paid_at = :paidAt,
                                   provider_transaction_id = :providerTransactionId,
                                   updated_at = CURRENT_TIMESTAMP,
                                   version = version + 1
                             WHERE id = :intentId
                               AND status = 'PENDING'
                               AND expires_at > :now
                            """,
            nativeQuery = true)
    int markPaidIfPending(
            @Param("intentId") UUID intentId,
            @Param("providerTransactionId") String providerTransactionId,
            @Param("paidAt") Instant paidAt,
            @Param("now") Instant now);
}
