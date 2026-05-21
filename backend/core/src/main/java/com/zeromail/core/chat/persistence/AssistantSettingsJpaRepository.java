package com.zeromail.core.chat.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantSettingsJpaRepository
        extends JpaRepository<AssistantSettingsEntity, UUID> {

    Optional<AssistantSettingsEntity> findByTenantId(UUID tenantId);
}
