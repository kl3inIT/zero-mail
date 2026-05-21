package com.zeromail.core.chat.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantMemoryJpaRepository extends JpaRepository<AssistantMemoryEntity, UUID> {

    List<AssistantMemoryEntity> findByTenantIdAndContentContainingIgnoreCase(
            UUID tenantId, String query, Pageable pageable);
}
