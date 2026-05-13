package com.zeromail.core.notification.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DigestDeliveryRepository extends JpaRepository<DigestDeliveryEntity, UUID> {

    Optional<DigestDeliveryEntity> findByTenantIdAndDigestDayLocal(
            UUID tenantId, LocalDate digestDayLocal);

    void deleteByTenantId(UUID tenantId);
}
