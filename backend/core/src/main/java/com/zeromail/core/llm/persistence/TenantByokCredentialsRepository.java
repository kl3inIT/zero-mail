package com.zeromail.core.llm.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantByokCredentialsRepository
        extends JpaRepository<TenantByokCredentialsEntity, UUID> {

    Optional<TenantByokCredentialsEntity> findByTenantId(UUID tenantId);
}
