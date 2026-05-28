package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    Optional<SubscriptionEntity> findByTenantId(UUID tenantId);

    Optional<SubscriptionEntity> findByLemonSqueezySubscriptionId(long lemonSqueezySubscriptionId);

    @Query(
            "SELECT s FROM SubscriptionEntity s "
                    + "WHERE s.status IN :statuses AND s.currentPeriodEnd <= :before")
    List<SubscriptionEntity> findExpiringBefore(
            @Param("statuses") List<String> statuses, @Param("before") Instant before);
}
