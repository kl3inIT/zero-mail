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

    @Query(
            value =
                    """
      SELECT *
      FROM rules
      WHERE tenant_id = :tenantId
        AND matcher_ast = CAST(:matcherAst AS jsonb)
        AND action_intents = CAST(:actionIntents AS jsonb)
      ORDER BY order_index ASC
      LIMIT 1
      """,
            nativeQuery = true)
    Optional<RuleEntity> findFirstByTenantIdAndDefinition(
            @Param("tenantId") UUID tenantId,
            @Param("matcherAst") String matcherAst,
            @Param("actionIntents") String actionIntents);

    @Query(
            value =
                    """
      SELECT *
      FROM rules
      WHERE tenant_id = :tenantId
        AND id <> :excludedRuleId
        AND matcher_ast = CAST(:matcherAst AS jsonb)
        AND action_intents = CAST(:actionIntents AS jsonb)
      ORDER BY order_index ASC
      LIMIT 1
      """,
            nativeQuery = true)
    Optional<RuleEntity> findFirstByTenantIdAndDefinitionExcludingRule(
            @Param("tenantId") UUID tenantId,
            @Param("excludedRuleId") UUID excludedRuleId,
            @Param("matcherAst") String matcherAst,
            @Param("actionIntents") String actionIntents);
}
