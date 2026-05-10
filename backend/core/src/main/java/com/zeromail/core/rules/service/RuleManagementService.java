package com.zeromail.core.rules.service;


import java.time.Instant;
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

import com.zeromail.core.rules.application.RuleCreateCommand;
import com.zeromail.core.rules.application.RuleOrderEntry;
import com.zeromail.core.rules.application.RuleReorderCommand;
import com.zeromail.core.rules.projection.RuleStatusProjection;
import com.zeromail.core.rules.application.RuleUpdateCommand;
import com.zeromail.core.rules.exception.RuleValidationException;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.rules.persistence.lowlevel.RuleNativeStateUpdater;

@Service
public class RuleManagementService {

  private final RuleRepository ruleRepository;
  private final RuleNativeStateUpdater ruleNativeStateUpdater;

  public RuleManagementService(
      RuleRepository ruleRepository, RuleNativeStateUpdater ruleNativeStateUpdater) {
    this.ruleRepository = ruleRepository;
    this.ruleNativeStateUpdater = ruleNativeStateUpdater;
  }

  @Transactional(readOnly = true)
  public List<RuleStatusProjection> listOrdered(UUID tenantId) {
    return ruleRepository.findOrderedByTenantId(tenantId).stream()
        .map(RuleEntity::toStatusProjection)
        .toList();
  }

  @Transactional(readOnly = true)
  public RuleStatusProjection get(UUID tenantId, UUID ruleId) {
    return findRuleOrThrow(tenantId, ruleId).toStatusProjection();
  }

  @Transactional
  public RuleStatusProjection create(RuleCreateCommand command) {
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
    return savedRule.toStatusProjection();
  }

  @Transactional
  public RuleStatusProjection update(RuleUpdateCommand command) {
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
    return ruleEntity.toStatusProjection();
  }

  @Transactional
  public RuleStatusProjection markPreviewSucceeded(
      UUID tenantId, UUID ruleId, Integer previewedEntityVersion, Instant previewedAt) {
    RuleEntity ruleEntity = findRuleOrThrow(tenantId, ruleId);
    if (!Objects.equals(ruleEntity.getEntityVersion(), previewedEntityVersion)) {
      throw RuleValidationException.versionMismatch();
    }
    Instant effectivePreviewedAt = previewedAt == null ? Instant.now() : previewedAt;
    boolean previewMarked =
        ruleNativeStateUpdater.markPreviewSucceeded(
            tenantId, ruleId, previewedEntityVersion, effectivePreviewedAt);
    if (!previewMarked) {
      throw RuleValidationException.versionMismatch();
    }
    return findRuleOrThrow(tenantId, ruleId).toStatusProjection();
  }

  @Transactional
  public RuleStatusProjection enable(UUID tenantId, UUID ruleId) {
    RuleEntity ruleEntity = findRuleOrThrow(tenantId, ruleId);
    if (!Objects.equals(ruleEntity.getLastPreviewedEntityVersion(), ruleEntity.getEntityVersion())) {
      throw RuleValidationException.previewRequired();
    }
    updateEnabled(tenantId, ruleId, true, ruleEntity.getEntityVersion());
    return findRuleOrThrow(tenantId, ruleId).toStatusProjection();
  }

  @Transactional
  public RuleStatusProjection disable(UUID tenantId, UUID ruleId) {
    RuleEntity ruleEntity = findRuleOrThrow(tenantId, ruleId);
    updateEnabled(tenantId, ruleId, false, ruleEntity.getEntityVersion());
    return findRuleOrThrow(tenantId, ruleId).toStatusProjection();
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
  public List<RuleStatusProjection> reorder(RuleReorderCommand command) {
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
    return reorderedRules.stream().map(RuleEntity::toStatusProjection).toList();
  }

  private RuleEntity findRuleOrThrow(UUID tenantId, UUID ruleId) {
    return ruleRepository.findByIdAndTenantId(ruleId, tenantId).orElseThrow(RuleValidationException::notFound);
  }

  private void updateEnabled(UUID tenantId, UUID ruleId, boolean enabled, Integer entityVersion) {
    boolean enabledUpdated =
        ruleNativeStateUpdater.updateEnabled(tenantId, ruleId, enabled, entityVersion);
    if (!enabledUpdated) {
      throw RuleValidationException.versionMismatch();
    }
  }

  private static void normalizeOrder(List<RuleEntity> rules) {
    for (int orderIndex = 0; orderIndex < rules.size(); orderIndex++) {
      rules.get(orderIndex).setOrderIndex(orderIndex);
    }
  }
}
