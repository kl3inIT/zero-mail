package com.zeromail.core.llm.byok;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserByokKeyRepository extends JpaRepository<UserByokKeyEntity, UUID> {

    Optional<UserByokKeyEntity> findByTenantId(UUID tenantId);
}
