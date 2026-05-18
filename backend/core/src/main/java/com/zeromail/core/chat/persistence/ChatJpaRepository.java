package com.zeromail.core.chat.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@SuppressWarnings("unused")
public interface ChatJpaRepository extends JpaRepository<ChatEntity, UUID> {

    List<ChatEntity> findByTenantIdAndSoftDeletedAtIsNullOrderByUpdatedAtDesc(
            UUID tenantId, Pageable pageable);
}
