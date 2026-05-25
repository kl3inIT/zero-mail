package com.zeromail.core.cleanup.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SenderSuppressionRepository extends JpaRepository<SenderSuppressionEntity, UUID> {

    List<SenderSuppressionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<SenderSuppressionEntity> findByTenantIdAndSenderEmail(
            UUID tenantId, String senderEmail);

    Optional<SenderSuppressionEntity> findByTenantIdAndSenderDomain(
            UUID tenantId, String senderDomain);

    Optional<SenderSuppressionEntity> findByIdAndTenantId(UUID suppressionId, UUID tenantId);
}
