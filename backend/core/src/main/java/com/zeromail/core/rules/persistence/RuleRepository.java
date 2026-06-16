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

    @Query(
            """
      SELECT ruleEntity
      FROM RuleEntity ruleEntity
      WHERE ruleEntity.tenantId = :tenantId
        AND ruleEntity.gmailConnectionId = :gmailConnectionId
      ORDER BY ruleEntity.orderIndex ASC
      """)
    List<RuleEntity> findOrderedByTenantIdAndGmailConnectionId(
            @Param("tenantId") UUID tenantId, @Param("gmailConnectionId") UUID gmailConnectionId);

    @Query(
            """
      SELECT ruleEntity
      FROM RuleEntity ruleEntity
      WHERE ruleEntity.tenantId = :tenantId
        AND ruleEntity.gmailConnectionId = :gmailConnectionId
        AND ruleEntity.enabled = true
      ORDER BY ruleEntity.orderIndex ASC
      """)
    List<RuleEntity> findEnabledByTenantIdAndGmailConnectionIdOrderByOrderIndex(
            @Param("tenantId") UUID tenantId, @Param("gmailConnectionId") UUID gmailConnectionId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndGmailConnectionId(UUID tenantId, UUID gmailConnectionId);

    Optional<RuleEntity> findByIdAndTenantId(UUID ruleId, UUID tenantId);

    Optional<RuleEntity> findByIdAndTenantIdAndGmailConnectionId(
            UUID ruleId, UUID tenantId, UUID gmailConnectionId);

    long deleteByIdAndTenantId(UUID ruleId, UUID tenantId);

    Optional<RuleEntity> findByTenantIdAndTemplateKey(UUID tenantId, String templateKey);

    Optional<RuleEntity> findByTenantIdAndGmailConnectionIdAndTemplateKey(
            UUID tenantId, UUID gmailConnectionId, String templateKey);

    List<RuleEntity> findByTenantIdAndTemplateKeyIn(UUID tenantId, Collection<String> templateKeys);

    List<RuleEntity> findByTenantIdAndGmailConnectionIdAndTemplateKeyIn(
            UUID tenantId, UUID gmailConnectionId, Collection<String> templateKeys);

    @Query(
            value =
                    """
      SELECT *
      FROM rules
      WHERE tenant_id = :tenantId
        AND gmail_connection_id = :gmailConnectionId
        AND matcher_ast = CAST(:matcherAst AS jsonb)
        AND action_intents = CAST(:actionIntents AS jsonb)
      ORDER BY order_index ASC
      LIMIT 1
      """,
            nativeQuery = true)
    Optional<RuleEntity> findFirstByTenantIdAndGmailConnectionIdAndDefinition(
            @Param("tenantId") UUID tenantId,
            @Param("gmailConnectionId") UUID gmailConnectionId,
            @Param("matcherAst") String matcherAst,
            @Param("actionIntents") String actionIntents);

    @Query(
            value =
                    """
      SELECT *
      FROM rules
      WHERE tenant_id = :tenantId
        AND gmail_connection_id = :gmailConnectionId
        AND id <> :excludedRuleId
        AND matcher_ast = CAST(:matcherAst AS jsonb)
        AND action_intents = CAST(:actionIntents AS jsonb)
      ORDER BY order_index ASC
      LIMIT 1
      """,
            nativeQuery = true)
    Optional<RuleEntity> findFirstByTenantIdAndGmailConnectionIdAndDefinitionExcludingRule(
            @Param("tenantId") UUID tenantId,
            @Param("gmailConnectionId") UUID gmailConnectionId,
            @Param("excludedRuleId") UUID excludedRuleId,
            @Param("matcherAst") String matcherAst,
            @Param("actionIntents") String actionIntents);
}
