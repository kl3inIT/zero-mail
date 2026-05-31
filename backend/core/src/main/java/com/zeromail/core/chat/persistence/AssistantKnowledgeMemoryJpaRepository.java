package com.zeromail.core.chat.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@SuppressWarnings("unused")
public interface AssistantKnowledgeMemoryJpaRepository
        extends JpaRepository<AssistantKnowledgeMemoryEntity, UUID> {

    List<AssistantKnowledgeMemoryEntity> findAllByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    Optional<AssistantKnowledgeMemoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            """
            select knowledgeMemory
            from AssistantKnowledgeMemoryEntity knowledgeMemory
            where knowledgeMemory.tenantId = :tenantId
              and (
                lower(knowledgeMemory.title) like lower(concat('%', :query, '%'))
                or lower(knowledgeMemory.content) like lower(concat('%', :query, '%'))
              )
            order by knowledgeMemory.updatedAt desc
            """)
    List<AssistantKnowledgeMemoryEntity> searchByTenantIdAndQuery(
            @Param("tenantId") UUID tenantId, @Param("query") String query, Pageable pageable);
}
