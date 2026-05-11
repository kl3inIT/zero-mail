package com.zeromail.core.triage.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSenderOptInRepository extends JpaRepository<TenantSenderOptInEntity, UUID> {

  boolean existsByTenantIdAndSenderEmail(UUID tenantId, String senderEmail);

  List<TenantSenderOptInEntity> findByTenantId(UUID tenantId);
}
