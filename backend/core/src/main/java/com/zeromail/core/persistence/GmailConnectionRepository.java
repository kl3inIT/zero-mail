package com.zeromail.core.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailConnectionRepository extends JpaRepository<GmailConnectionEntity, UUID> {

    Optional<GmailConnectionEntity> findByTenantId(UUID tenantId);
}
