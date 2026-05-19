package com.zeromail.core.chat.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@SuppressWarnings("unused")
public interface AssistantActionAuditJpaRepository
        extends JpaRepository<AssistantActionAuditEntity, UUID> {

    Optional<AssistantActionAuditEntity> findByChatIdAndToolCallId(UUID chatId, String toolCallId);

    List<AssistantActionAuditEntity> findByTenantIdAndToolCategoryOrderBySentAtDesc(
            UUID tenantId, String toolCategory, Pageable pageable);
}
