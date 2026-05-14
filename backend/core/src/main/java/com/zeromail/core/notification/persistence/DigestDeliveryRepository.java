package com.zeromail.core.notification.persistence;

import com.zeromail.core.notification.domain.DigestDeliveryStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DigestDeliveryRepository extends JpaRepository<DigestDeliveryEntity, UUID> {

    Optional<DigestDeliveryEntity> findByTenantIdAndDigestDayLocal(
            UUID tenantId, LocalDate digestDayLocal);

    @Query(
            """
            select digestDelivery
            from DigestDeliveryEntity digestDelivery
            where digestDelivery.status = :status
              and digestDelivery.createdAt < :cutoff
            order by digestDelivery.createdAt asc
            """)
    List<DigestDeliveryEntity> findStuckByStatusBefore(
            DigestDeliveryStatus status, Instant cutoff, Pageable pageable);

    void deleteByTenantId(UUID tenantId);
}
