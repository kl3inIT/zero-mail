package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPaymentEventRepository
        extends JpaRepository<BillingPaymentEventEntity, UUID> {

    @Modifying
    @Query(
            value =
                    """
                            INSERT INTO billing_payment_event (
                                id,
                                tenant_id,
                                payment_attempt_id,
                                topup_intent_id,
                                provider,
                                provider_event_id,
                                event_type,
                                processing_status,
                                received_at,
                                created_at,
                                updated_at,
                                version
                            ) VALUES (
                                :id,
                                :tenantId,
                                :paymentAttemptId,
                                :topupIntentId,
                                :provider,
                                :providerEventId,
                                :eventType,
                                'RECEIVED',
                                :receivedAt,
                                :receivedAt,
                                :receivedAt,
                                0
                            )
                            ON CONFLICT (provider, provider_event_id) DO NOTHING
                            """,
            nativeQuery = true)
    int insertReceivedIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("paymentAttemptId") UUID paymentAttemptId,
            @Param("topupIntentId") UUID topupIntentId,
            @Param("provider") String provider,
            @Param("providerEventId") String providerEventId,
            @Param("eventType") String eventType,
            @Param("receivedAt") Instant receivedAt);

    @Modifying
    @Query(
            value =
                    """
                            UPDATE billing_payment_event
                               SET processing_status = 'PROCESSED',
                                   processed_at = :processedAt,
                                   updated_at = CURRENT_TIMESTAMP,
                                   version = version + 1
                             WHERE provider = :provider
                               AND provider_event_id = :providerEventId
                            """,
            nativeQuery = true)
    int markProcessed(
            @Param("provider") String provider,
            @Param("providerEventId") String providerEventId,
            @Param("processedAt") Instant processedAt);

    @Modifying
    @Query(
            value =
                    """
                            UPDATE billing_payment_event
                               SET processing_status = 'IGNORED',
                                   processed_at = :processedAt,
                                   updated_at = CURRENT_TIMESTAMP,
                                   version = version + 1
                             WHERE provider = :provider
                               AND provider_event_id = :providerEventId
                            """,
            nativeQuery = true)
    int markIgnored(
            @Param("provider") String provider,
            @Param("providerEventId") String providerEventId,
            @Param("processedAt") Instant processedAt);
}
