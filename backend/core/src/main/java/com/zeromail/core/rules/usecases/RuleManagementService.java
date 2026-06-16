package com.zeromail.core.rules.usecases;

import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.rules.exception.RuleValidationException;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.rules.persistence.lowlevel.RuleNativeStateUpdater;
import com.zeromail.core.rules.projection.EnabledRuleSnapshot;
import com.zeromail.core.rules.projection.RuleStatusProjection;
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

@Service
public class RuleManagementService {

    private final RuleRepository ruleRepository;
    private final RuleNativeStateUpdater ruleNativeStateUpdater;
    private final GmailConnectionService gmailConnectionService;

    public RuleManagementService(
            RuleRepository ruleRepository,
            RuleNativeStateUpdater ruleNativeStateUpdater,
            GmailConnectionService gmailConnectionService) {
        this.ruleRepository = ruleRepository;
        this.ruleNativeStateUpdater = ruleNativeStateUpdater;
        this.gmailConnectionService = gmailConnectionService;
    }

    @Transactional(readOnly = true)
    public List<RuleStatusProjection> listOrdered(UUID tenantId) {
        return listOrdered(tenantId, primaryGmailConnectionIdOrThrow(tenantId));
    }

    @Transactional(readOnly = true)
    public List<RuleStatusProjection> listOrdered(UUID tenantId, UUID gmailConnectionId) {
        return ruleRepository
                .findOrderedByTenantIdAndGmailConnectionId(tenantId, gmailConnectionId)
                .stream()
                .map(RuleEntity::toStatusProjection)
                .toList();
    }

    /**
     * Read-side accessor used by the triage orchestrator to build per-message execution candidates
     * without depending on {@link RuleRepository} across domain boundaries.
     */
    @Transactional(readOnly = true)
    public List<EnabledRuleSnapshot> listEnabledForExecution(UUID tenantId) {
        return listEnabledForExecution(tenantId, primaryGmailConnectionIdOrThrow(tenantId));
    }

    @Transactional(readOnly = true)
    public List<EnabledRuleSnapshot> listEnabledForExecution(UUID tenantId, UUID sourceMailboxId) {
        return ruleRepository
                .findEnabledByTenantIdAndGmailConnectionIdOrderByOrderIndex(
                        tenantId, sourceMailboxId)
                .stream()
                .map(RuleManagementService::toEnabledRuleSnapshot)
                .toList();
    }

    @Transactional(readOnly = true)
    public RuleStatusProjection get(UUID tenantId, UUID ruleId) {
        return get(tenantId, primaryGmailConnectionIdOrThrow(tenantId), ruleId);
    }

    @Transactional(readOnly = true)
    public RuleStatusProjection get(UUID tenantId, UUID gmailConnectionId, UUID ruleId) {
        return findRuleOrThrow(tenantId, gmailConnectionId, ruleId).toStatusProjection();
    }

    @Transactional
    public RuleStatusProjection create(RuleCreateCommand command) {
        rejectDuplicateDefinition(
                command.tenantId(),
                command.gmailConnectionId(),
                command.compileResult().matcherAst(),
                command.compileResult().actionIntents(),
                null);
        int orderIndex =
                (int)
                        ruleRepository.countByTenantIdAndGmailConnectionId(
                                command.tenantId(), command.gmailConnectionId());
        RuleEntity ruleEntity =
                new RuleEntity(
                        command.ruleId(),
                        command.tenantId(),
                        command.gmailConnectionId(),
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
    public RuleStatusProjection createOrEnable(RuleCreateCommand command) {
        if (command.templateKey() != null) {
            var existingRule =
                    ruleRepository.findByTenantIdAndGmailConnectionIdAndTemplateKey(
                            command.tenantId(), command.gmailConnectionId(), command.templateKey());
            if (existingRule.isPresent()) {
                RuleEntity ruleEntity = existingRule.orElseThrow();
                if (!ruleEntity.isEnabled()) {
                    updateEnabled(command.tenantId(), ruleEntity, true);
                }
                return ruleEntity.toStatusProjection();
            }
        }

        RuleStatusProjection createdRule = create(command);
        return enable(
                command.tenantId(), command.gmailConnectionId(), createdRule.ruleId().value());
    }

    @Transactional
    public RuleStatusProjection update(RuleUpdateCommand command) {
        RuleEntity ruleEntity =
                findRuleOrThrow(command.tenantId(), command.gmailConnectionId(), command.ruleId());
        if (!Objects.equals(ruleEntity.getEntityVersion(), command.expectedEntityVersion())) {
            throw RuleValidationException.versionMismatch();
        }
        boolean customizedDefinition =
                !Objects.equals(ruleEntity.getSourceText(), command.sourceText())
                        || !Objects.equals(
                                ruleEntity.getMatcherAst(), command.compileResult().matcherAst())
                        || !Objects.equals(
                                ruleEntity.getActionIntents(),
                                command.compileResult().actionIntents());

        rejectDuplicateDefinition(
                command.tenantId(),
                command.gmailConnectionId(),
                command.compileResult().matcherAst(),
                command.compileResult().actionIntents(),
                command.ruleId());

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
        return markPreviewSucceeded(
                tenantId,
                primaryGmailConnectionIdOrThrow(tenantId),
                ruleId,
                previewedEntityVersion,
                previewedAt);
    }

    @Transactional
    public RuleStatusProjection markPreviewSucceeded(
            UUID tenantId,
            UUID gmailConnectionId,
            UUID ruleId,
            Integer previewedEntityVersion,
            Instant previewedAt) {
        RuleEntity ruleEntity = findRuleOrThrow(tenantId, gmailConnectionId, ruleId);
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
        ruleNativeStateUpdater.refresh(ruleEntity);
        return ruleEntity.toStatusProjection();
    }

    @Transactional
    public RuleStatusProjection enable(UUID tenantId, UUID ruleId) {
        return enable(tenantId, primaryGmailConnectionIdOrThrow(tenantId), ruleId);
    }

    @Transactional
    public RuleStatusProjection enable(UUID tenantId, UUID gmailConnectionId, UUID ruleId) {
        // Preview-before-enable gate intentionally removed: v1 write actions
        // (label / archive / save_draft) are reversible and the Test tab is a
        // separate first-class entry point — users no longer need to preview
        // before flipping the switch.
        RuleEntity ruleEntity = findRuleOrThrow(tenantId, gmailConnectionId, ruleId);
        updateEnabled(tenantId, ruleEntity, true);
        return ruleEntity.toStatusProjection();
    }

    @Transactional
    public RuleStatusProjection disable(UUID tenantId, UUID ruleId) {
        return disable(tenantId, primaryGmailConnectionIdOrThrow(tenantId), ruleId);
    }

    @Transactional
    public RuleStatusProjection disable(UUID tenantId, UUID gmailConnectionId, UUID ruleId) {
        RuleEntity ruleEntity = findRuleOrThrow(tenantId, gmailConnectionId, ruleId);
        updateEnabled(tenantId, ruleEntity, false);
        return ruleEntity.toStatusProjection();
    }

    @Transactional
    public void delete(UUID tenantId, UUID ruleId) {
        delete(tenantId, primaryGmailConnectionIdOrThrow(tenantId), ruleId);
    }

    @Transactional
    public void delete(UUID tenantId, UUID gmailConnectionId, UUID ruleId) {
        RuleEntity ruleEntity = findRuleOrThrow(tenantId, gmailConnectionId, ruleId);
        ruleRepository.delete(ruleEntity);
        ruleRepository.flush();
        normalizeOrder(
                ruleRepository.findOrderedByTenantIdAndGmailConnectionId(
                        tenantId, gmailConnectionId));
        ruleRepository.flush();
    }

    @Transactional
    public List<RuleStatusProjection> reorder(RuleReorderCommand command) {
        UUID gmailConnectionId = primaryGmailConnectionIdOrThrow(command.tenantId());
        List<RuleEntity> currentRules =
                ruleRepository.findOrderedByTenantIdAndGmailConnectionId(
                        command.tenantId(), gmailConnectionId);
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

    private RuleEntity findRuleOrThrow(UUID tenantId, UUID gmailConnectionId, UUID ruleId) {
        return ruleRepository
                .findByIdAndTenantIdAndGmailConnectionId(ruleId, tenantId, gmailConnectionId)
                .orElseThrow(RuleValidationException::notFound);
    }

    private UUID primaryGmailConnectionIdOrThrow(UUID tenantId) {
        return gmailConnectionService
                .primaryMailboxRef(tenantId)
                .map(MailboxRef::gmailConnectionId)
                .orElseThrow(RuleValidationException::notFound);
    }

    private static EnabledRuleSnapshot toEnabledRuleSnapshot(RuleEntity ruleEntity) {
        return new EnabledRuleSnapshot(
                ruleEntity.getId(),
                ruleEntity.getDisplayName(),
                ruleEntity.getOrderIndex(),
                ruleEntity.getMatcherAst(),
                ruleEntity.getActionIntents());
    }

    private void updateEnabled(UUID tenantId, RuleEntity ruleEntity, boolean enabled) {
        boolean enabledUpdated =
                ruleNativeStateUpdater.updateEnabled(
                        tenantId, ruleEntity.getId(), enabled, ruleEntity.getEntityVersion());
        if (!enabledUpdated) {
            throw RuleValidationException.versionMismatch();
        }
        ruleNativeStateUpdater.refresh(ruleEntity);
    }

    private void rejectDuplicateDefinition(
            UUID tenantId,
            UUID gmailConnectionId,
            String matcherAst,
            String actionIntents,
            UUID excludedRuleId) {
        boolean duplicateExists =
                excludedRuleId == null
                        ? ruleRepository
                                .findFirstByTenantIdAndGmailConnectionIdAndDefinition(
                                        tenantId, gmailConnectionId, matcherAst, actionIntents)
                                .isPresent()
                        : ruleRepository
                                .findFirstByTenantIdAndGmailConnectionIdAndDefinitionExcludingRule(
                                        tenantId,
                                        gmailConnectionId,
                                        excludedRuleId,
                                        matcherAst,
                                        actionIntents)
                                .isPresent();
        if (duplicateExists) {
            throw RuleValidationException.duplicate();
        }
    }

    private static void normalizeOrder(List<RuleEntity> rules) {
        for (int orderIndex = 0; orderIndex < rules.size(); orderIndex++) {
            rules.get(orderIndex).setOrderIndex(orderIndex);
        }
    }
}
