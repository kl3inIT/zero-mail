package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingWebhookEventRepository
        extends JpaRepository<BillingWebhookEventEntity, UUID> {

    Optional<BillingWebhookEventEntity> findByDedupeKey(String dedupeKey);

    Optional<BillingWebhookEventEntity> findByProviderEventId(String providerEventId);

    @Query(
            "SELECT event FROM BillingWebhookEventEntity event "
                    + "WHERE event.processingStatus = :status AND event.receivedAt < :before")
    List<BillingWebhookEventEntity> findStuckByStatus(
            @Param("status") String status, @Param("before") Instant before);
}
