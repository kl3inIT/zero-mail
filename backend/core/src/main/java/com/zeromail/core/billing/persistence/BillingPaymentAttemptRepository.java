package com.zeromail.core.billing.persistence;

import com.zeromail.core.billing.domain.PaymentProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPaymentAttemptRepository
        extends JpaRepository<BillingPaymentAttemptEntity, UUID> {

    Optional<BillingPaymentAttemptEntity> findByProviderAndProviderReferenceId(
            PaymentProvider provider, String providerReferenceId);

    Optional<BillingPaymentAttemptEntity> findFirstByTopupIntentIdAndProviderOrderByCreatedAtDesc(
            UUID topupIntentId, PaymentProvider provider);

    @Modifying
    @Query(
            value =
                    """
            INSERT INTO billing_payment_attempt (
                id,
                tenant_id,
                topup_intent_id,
                provider,
                status,
                amount,
                currency,
                provider_reference_id,
                expires_at,
                created_at,
                updated_at,
                version
            ) VALUES (
                :id,
                :tenantId,
                :topupIntentId,
                'SEPAY',
                'PENDING',
                :amountVnd,
                'vnd',
                :orderCode,
                :expiresAt,
                :createdAt,
                :createdAt,
                0
            )
            ON CONFLICT (provider, provider_reference_id)
            WHERE provider_reference_id IS NOT NULL
            DO NOTHING
            """,
            nativeQuery = true)
    int insertSepayPendingIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("topupIntentId") UUID topupIntentId,
            @Param("amountVnd") long amountVnd,
            @Param("orderCode") String orderCode,
            @Param("expiresAt") Instant expiresAt,
            @Param("createdAt") Instant createdAt);

    @Modifying
    @Query(
            value =
                    """
                            UPDATE billing_payment_attempt
                               SET status = 'EXPIRED',
                                   updated_at = CURRENT_TIMESTAMP,
                                   version = version + 1
                             WHERE status = 'PENDING'
                               AND expires_at < :now
                            """,
            nativeQuery = true)
    int expireStale(@Param("now") Instant now);
}
