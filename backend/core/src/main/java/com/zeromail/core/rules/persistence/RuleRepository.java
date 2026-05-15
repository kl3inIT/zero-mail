package com.zeromail.core.rules.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {

    @Query(
            """
      SELECT ruleEntity
      FROM RuleEntity ruleEntity
      WHERE ruleEntity.tenantId = :tenantId
      ORDER BY ruleEntity.orderIndex ASC
      """)
    List<RuleEntity> findOrderedByTenantId(@Param("tenantId") UUID tenantId);

    long countByTenantId(UUID tenantId);

    Optional<RuleEntity> findByIdAndTenantId(UUID ruleId, UUID tenantId);

    long deleteByIdAndTenantId(UUID ruleId, UUID tenantId);

    Optional<RuleEntity> findByTenantIdAndTemplateKey(UUID tenantId, String templateKey);

    List<RuleEntity> findByTenantIdAndTemplateKeyIn(UUID tenantId, Collection<String> templateKeys);
}
