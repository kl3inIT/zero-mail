package com.zeromail.core.chat.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@SuppressWarnings("unused")
public interface AssistantKnowledgeMemoryJpaRepository
        extends JpaRepository<AssistantKnowledgeMemoryEntity, UUID> {

    List<AssistantKnowledgeMemoryEntity> findAllByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<AssistantKnowledgeMemoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
