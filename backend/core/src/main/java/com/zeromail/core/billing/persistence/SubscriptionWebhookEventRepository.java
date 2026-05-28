package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionWebhookEventRepository
        extends JpaRepository<SubscriptionWebhookEventEntity, UUID> {

    Optional<SubscriptionWebhookEventEntity> findByDedupeKey(String dedupeKey);

    Optional<SubscriptionWebhookEventEntity> findByProviderEventId(String providerEventId);

    @Query(
            "SELECT e FROM SubscriptionWebhookEventEntity e "
                    + "WHERE e.processingStatus = :status AND e.receivedAt <= :before "
                    + "ORDER BY e.receivedAt ASC")
    List<SubscriptionWebhookEventEntity> findStuckByStatus(
            @Param("status") String status, @Param("before") Instant before);
}
