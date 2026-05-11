package com.zeromail.core.rules.service;

import com.zeromail.core.onboarding.service.OnboardingService;
import com.zeromail.core.rules.application.RuleTemplateMaterializationResult;
import com.zeromail.core.rules.application.RuleTemplateMaterializationResult.SkippedTemplate;
import com.zeromail.core.rules.application.RuleTemplateMaterializationResult.SkippedTemplateReason;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.rules.persistence.RuleTemplateEntity;
import com.zeromail.core.rules.projection.RuleStatusProjection;
import com.zeromail.core.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RuleTemplateMaterializationService {

    private static final Logger log =
            LoggerFactory.getLogger(RuleTemplateMaterializationService.class);

    private final OnboardingService onboardingService;
    private final RuleTemplateCatalogService ruleTemplateCatalogService;
    private final RuleRepository ruleRepository;
    private final TransactionTemplate transactionTemplate;

    public RuleTemplateMaterializationService(
            OnboardingService onboardingService,
            RuleTemplateCatalogService ruleTemplateCatalogService,
            RuleRepository ruleRepository,
            PlatformTransactionManager transactionManager) {
        this.onboardingService = onboardingService;
        this.ruleTemplateCatalogService = ruleTemplateCatalogService;
        this.ruleRepository = ruleRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public RuleTemplateMaterializationResult materializeSelectedTemplates(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        List<String> selectedTemplateKeys = onboardingService.selectedEnabledTemplateKeys(tenantId);
        List<RuleStatusProjection> createdRules = new ArrayList<>();
        List<SkippedTemplate> skippedTemplates = new ArrayList<>();
        int customizedPreservedCount = 0;

        for (String selectedTemplateKey : selectedTemplateKeys) {
            TemplateMaterializationOutcome materializationOutcome =
                    materializeTemplateWithRetry(tenantId, selectedTemplateKey);
            materializationOutcome.createdRule().ifPresent(createdRules::add);
            materializationOutcome.skippedTemplate().ifPresent(skippedTemplates::add);
            if (materializationOutcome.customizedPreserved()) {
                customizedPreservedCount++;
            }
        }

        long skippedDeprecatedCount =
                skippedTemplates.stream()
                        .filter(
                                skippedTemplate ->
                                        skippedTemplate.reason()
                                                == SkippedTemplateReason.UNKNOWN_OR_DEPRECATED)
                        .count();
        RuleTemplateMaterializationResult materializationResult =
                new RuleTemplateMaterializationResult(
                        createdRules.size(),
                        skippedTemplates.size(),
                        customizedPreservedCount,
                        createdRules,
                        skippedTemplates);
        log.info(
                "event=rules_templates_materialized tenantId={} created={} skipped={} customizedPreserved={} skippedDeprecated={}",
                tenantId,
                materializationResult.createdCount(),
                materializationResult.skippedCount(),
                materializationResult.customizedPreservedCount(),
                skippedDeprecatedCount);
        return materializationResult;
    }

    public RuleTemplateMaterializationResult materializeTemplate(
            UUID tenantId, String templateKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey must not be blank");
        }
        TemplateMaterializationOutcome materializationOutcome =
                materializeTemplateWithRetry(tenantId, templateKey.trim());
        List<RuleStatusProjection> createdRules =
                materializationOutcome.createdRule().map(List::of).orElseGet(List::of);
        List<SkippedTemplate> skippedTemplates =
                materializationOutcome.skippedTemplate().map(List::of).orElseGet(List::of);
        return new RuleTemplateMaterializationResult(
                createdRules.size(),
                skippedTemplates.size(),
                materializationOutcome.customizedPreserved() ? 1 : 0,
                createdRules,
                skippedTemplates);
    }

    private TemplateMaterializationOutcome materializeTemplateWithRetry(
            UUID tenantId, String templateKey) {
        try {
            return executeInTenantTransaction(
                    tenantId, () -> materializeTemplateOnce(tenantId, templateKey));
        } catch (DataIntegrityViolationException dataIntegrityViolation) {
            return executeInTenantTransaction(
                    tenantId,
                    () ->
                            reloadAfterConcurrentMaterialization(
                                    tenantId, templateKey, dataIntegrityViolation));
        }
    }

    private TemplateMaterializationOutcome materializeTemplateOnce(
            UUID tenantId, String templateKey) {
        Optional<RuleEntity> existingRule =
                ruleRepository.findByTenantIdAndTemplateKey(tenantId, templateKey);
        if (existingRule.isPresent()) {
            return skippedExistingTemplateRule(
                    existingRule.get(), templateKey, SkippedTemplateReason.ALREADY_MATERIALIZED);
        }

        Optional<RuleTemplateEntity> template =
                ruleTemplateCatalogService.resolveLatestMaterializableTemplate(templateKey);
        if (template.isEmpty()) {
            return TemplateMaterializationOutcome.skipped(
                    templateKey, SkippedTemplateReason.UNKNOWN_OR_DEPRECATED, false);
        }

        int orderIndex = ruleRepository.findOrderedByTenantId(tenantId).size();
        RuleTemplateEntity ruleTemplateEntity = template.get();
        RuleEntity ruleEntity =
                new RuleEntity(
                        UUID.randomUUID(),
                        tenantId,
                        ruleTemplateEntity.getDisplayName(),
                        ruleTemplateEntity.getSourceText(),
                        ruleTemplateEntity.getSourceLanguage(),
                        ruleTemplateEntity.getSchemaVersion(),
                        ruleTemplateEntity.getMatcherAst(),
                        ruleTemplateEntity.getActionIntents(),
                        orderIndex,
                        ruleTemplateEntity.getTemplateKey(),
                        ruleTemplateEntity.getTemplateVersion());

        RuleEntity savedRule = ruleRepository.saveAndFlush(ruleEntity);
        return TemplateMaterializationOutcome.created(savedRule.toStatusProjection());
    }

    private TemplateMaterializationOutcome reloadAfterConcurrentMaterialization(
            UUID tenantId,
            String templateKey,
            DataIntegrityViolationException dataIntegrityViolation) {
        Optional<RuleEntity> existingRule =
                ruleRepository.findByTenantIdAndTemplateKey(tenantId, templateKey);
        if (existingRule.isEmpty()) {
            throw dataIntegrityViolation;
        }
        return skippedExistingTemplateRule(
                existingRule.get(), templateKey, SkippedTemplateReason.CONCURRENTLY_MATERIALIZED);
    }

    private static TemplateMaterializationOutcome skippedExistingTemplateRule(
            RuleEntity existingRule, String templateKey, SkippedTemplateReason defaultReason) {
        if (existingRule.isCustomized()) {
            return TemplateMaterializationOutcome.skipped(
                    templateKey, SkippedTemplateReason.CUSTOMIZED_PRESERVED, true);
        }
        return TemplateMaterializationOutcome.skipped(templateKey, defaultReason, false);
    }

    private <T> T executeInTenantTransaction(UUID tenantId, Supplier<T> operation) {
        return executeInTenantScope(
                tenantId,
                () ->
                        Objects.requireNonNull(
                                transactionTemplate.execute(transactionStatus -> operation.get())));
    }

    private static <T> T executeInTenantScope(UUID tenantId, Supplier<T> operation) {
        AtomicReference<T> scopedResult = new AtomicReference<>();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> scopedResult.set(operation.get()));
        return scopedResult.get();
    }

    private record TemplateMaterializationOutcome(
            Optional<RuleStatusProjection> createdRule,
            Optional<SkippedTemplate> skippedTemplate,
            boolean customizedPreserved) {

        private static TemplateMaterializationOutcome created(RuleStatusProjection createdRule) {
            return new TemplateMaterializationOutcome(
                    Optional.of(createdRule), Optional.empty(), false);
        }

        private static TemplateMaterializationOutcome skipped(
                String templateKey, SkippedTemplateReason reason, boolean customizedPreserved) {
            return new TemplateMaterializationOutcome(
                    Optional.empty(),
                    Optional.of(new SkippedTemplate(templateKey, reason)),
                    customizedPreserved);
        }
    }
}
