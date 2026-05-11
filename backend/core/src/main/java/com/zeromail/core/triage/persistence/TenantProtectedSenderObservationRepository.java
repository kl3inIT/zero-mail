package com.zeromail.core.triage.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantProtectedSenderObservationRepository
    extends JpaRepository<TenantProtectedSenderObservationEntity, UUID> {

  Optional<TenantProtectedSenderObservationEntity> findByTenantIdAndSenderEmail(
      UUID tenantId, String senderEmail);

  List<TenantProtectedSenderObservationEntity> findByTenantId(UUID tenantId);
}
