package com.zeromail.core.rules.service;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.rules.model.RuleCreateCommand;
import com.zeromail.core.rules.model.RuleOrderEntry;
import com.zeromail.core.rules.model.RuleReorderCommand;
import com.zeromail.core.rules.model.RuleStatusView;
import com.zeromail.core.rules.model.RuleUpdateCommand;
import com.zeromail.core.rules.model.RuleValidationException;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;

import jakarta.persistence.EntityManager;

@Service
public class RuleManagementService {

  private final RuleRepository ruleRepository;
  private final EntityManager entityManager;

  public RuleManagementService(RuleRepository ruleRepository, EntityManager entityManager) {
    this.ruleRepository = ruleRepository;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public List<RuleStatusView> listOrdered(UUID tenantId) {
    return ruleRepository.findOrderedByTenantId(tenantId).stream().map(RuleEntity::toStatusView).toList();
  }

  @Transactional(readOnly = true)
  public RuleStatusView get(UUID tenantId, UUID ruleId) {
    return findRuleOrThrow(tenantId, ruleId).toStatusView();
  }

  @Transactional
  public RuleStatusView create(RuleCreateCommand command) {
    int orderIndex = ruleRepository.findOrderedByTenantId(command.tenantId()).size();
    RuleEntity ruleEntity =
        new RuleEntity(
            command.ruleId(),
            command.tenantId(),
            command.displayName(),
            command.sourceText(),
            command.compileResult().sourceLanguage(),
            command.compileResult().schemaVersion(),
            command.compileResult().matcherAst(),
            command.compileResult().actionIntents(),
            orderIndex,
            command.templateKey(),
            command.templateVersion());
    RuleEntity savedRule = ruleRepository.saveAndFlush(ruleEntity);
    return savedRule.toStatusView();
  }

  @Transactional
  public RuleStatusView update(RuleUpdateCommand command) {
    RuleEntity ruleEntity = findRuleOrThrow(command.tenantId(), command.ruleId());
    boolean customizedDefinition =
        !Objects.equals(ruleEntity.getSourceText(), command.sourceText())
            || !Objects.equals(ruleEntity.getMatcherAst(), command.compileResult().matcherAst())
            || !Objects.equals(ruleEntity.getActionIntents(), command.compileResult().actionIntents());

    ruleEntity.replaceDefinition(
        command.displayName(),
        command.sourceText(),
        command.compileResult().sourceLanguage(),
        command.compileResult().schemaVersion(),
        command.compileResult().matcherAst(),
        command.compileResult().actionIntents());
    ruleEntity.clearPreview();
    ruleEntity.setEnabled(false);
    if (customizedDefinition && ruleEntity.getTemplateKey() != null) {
      ruleEntity.markCustomized();
    }
    ruleRepository.flush();
    return ruleEntity.toStatusView();
  }

  @Transactional
  public RuleStatusView markPreviewSucceeded(
      UUID tenantId, UUID ruleId, Integer previewedEntityVersion, Instant previewedAt) {
    RuleEntity ruleEntity = findRuleOrThrow(tenantId, ruleId);
    if (!Objects.equals(ruleEntity.getEntityVersion(), previewedEntityVersion)) {
      throw RuleValidationException.versionMismatch();
    }
    Instant effectivePreviewedAt = previewedAt == null ? Instant.now() : previewedAt;
    int updatedRows =
        entityManager
            .createNativeQuery(
                """
                update rules
                set last_previewed_entity_version = ?,
                    last_previewed_at = ?,
                    updated_at = now()
                where tenant_id = ?
                  and id = ?
                  and version = ?
                """)
            .setParameter(1, previewedEntityVersion)
            .setParameter(2, Timestamp.from(effectivePreviewedAt))
            .setParameter(3, tenantId)
            .setParameter(4, ruleId)
            .setParameter(5, previewedEntityVersion)
            .executeUpdate();
    if (updatedRows != 1) {
      throw RuleValidationException.versionMismatch();
    }
    entityManager.clear();
    return findRuleOrThrow(tenantId, ruleId).toStatusView();
  }

  @Transactional
  public RuleStatusView enable(UUID tenantId, UUID ruleId) {
    RuleEntity ruleEntity = findRuleOrThrow(tenantId, ruleId);
    if (!Objects.equals(ruleEntity.getLastPreviewedEntityVersion(), ruleEntity.getEntityVersion())) {
      throw RuleValidationException.previewRequired();
    }
    updateEnabled(tenantId, ruleId, true, ruleEntity.getEntityVersion());
    return findRuleOrThrow(tenantId, ruleId).toStatusView();
  }

  @Transactional
  public RuleStatusView disable(UUID tenantId, UUID ruleId) {
    RuleEntity ruleEntity = findRuleOrThrow(tenantId, ruleId);
    updateEnabled(tenantId, ruleId, false, ruleEntity.getEntityVersion());
    return findRuleOrThrow(tenantId, ruleId).toStatusView();
  }

  @Transactional
  public void delete(UUID tenantId, UUID ruleId) {
    RuleEntity ruleEntity = findRuleOrThrow(tenantId, ruleId);
    ruleRepository.delete(ruleEntity);
    ruleRepository.flush();
    normalizeOrder(ruleRepository.findOrderedByTenantId(tenantId));
    ruleRepository.flush();
  }

  @Transactional
  public List<RuleStatusView> reorder(RuleReorderCommand command) {
    List<RuleEntity> currentRules = ruleRepository.findOrderedByTenantId(command.tenantId());
    Map<UUID, RuleEntity> currentRulesById = new LinkedHashMap<>();
    for (RuleEntity currentRule : currentRules) {
      currentRulesById.put(currentRule.getId(), currentRule);
    }

    Set<UUID> submittedRuleIds = new LinkedHashSet<>();
    for (RuleOrderEntry orderedEntry : command.orderedEntries()) {
      if (!submittedRuleIds.add(orderedEntry.ruleId())) {
        throw RuleValidationException.invalidReorder();
      }
    }
    if (!submittedRuleIds.equals(currentRulesById.keySet())) {
      throw RuleValidationException.invalidReorder();
    }

    for (RuleOrderEntry orderedEntry : command.orderedEntries()) {
      RuleEntity ruleEntity = currentRulesById.get(orderedEntry.ruleId());
      if (!Objects.equals(ruleEntity.getEntityVersion(), orderedEntry.entityVersion())) {
        throw RuleValidationException.versionMismatch();
      }
    }

    List<RuleEntity> reorderedRules = new ArrayList<>();
    for (int orderIndex = 0; orderIndex < command.orderedEntries().size(); orderIndex++) {
      RuleOrderEntry orderedEntry = command.orderedEntries().get(orderIndex);
      RuleEntity ruleEntity = currentRulesById.get(orderedEntry.ruleId());
      ruleEntity.setOrderIndex(orderIndex);
      reorderedRules.add(ruleEntity);
    }
    ruleRepository.flush();
    return reorderedRules.stream().map(RuleEntity::toStatusView).toList();
  }

  private RuleEntity findRuleOrThrow(UUID tenantId, UUID ruleId) {
    return ruleRepository.findByIdAndTenantId(ruleId, tenantId).orElseThrow(RuleValidationException::notFound);
  }

  private void updateEnabled(UUID tenantId, UUID ruleId, boolean enabled, Integer entityVersion) {
    int updatedRows =
        entityManager
            .createNativeQuery(
                """
                update rules
                set enabled = ?,
                    updated_at = now()
                where tenant_id = ?
                  and id = ?
                  and version = ?
                """)
            .setParameter(1, enabled)
            .setParameter(2, tenantId)
            .setParameter(3, ruleId)
            .setParameter(4, entityVersion)
            .executeUpdate();
    if (updatedRows != 1) {
      throw RuleValidationException.versionMismatch();
    }
    entityManager.clear();
  }

  private static void normalizeOrder(List<RuleEntity> rules) {
    for (int orderIndex = 0; orderIndex < rules.size(); orderIndex++) {
      rules.get(orderIndex).setOrderIndex(orderIndex);
    }
  }
}
