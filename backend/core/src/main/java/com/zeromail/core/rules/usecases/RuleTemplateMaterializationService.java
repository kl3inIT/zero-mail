package com.zeromail.core.rules.usecases;

import com.zeromail.core.onboarding.usecases.OnboardingService;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.rules.persistence.RuleTemplateEntity;
import com.zeromail.core.rules.projection.RuleStatusProjection;
import com.zeromail.core.rules.usecases.RuleTemplateMaterializationResult.SkippedTemplate;
import com.zeromail.core.rules.usecases.RuleTemplateMaterializationResult.SkippedTemplateReason;
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

    /**
     * Inbox-Zero-style starter rules seeded (enabled) on first login so a new tenant lands on a
     * populated Rules page. Keys mirror the {@code system-*} rows in {@code
     * 113-default-rule-templates-seed.yaml} (English) and {@code
     * 114-default-rule-templates-vi-seed.yaml} (Vietnamese, {@code -vi} suffix). Order here is the
     * order the rules appear in the Rules list (reply-status family first, then content
     * categories). First-login seeding picks the set matching the user's language; existing tenants
     * are not re-seeded (first login only).
     */
    public static final List<String> DEFAULT_RULE_TEMPLATE_KEYS_EN =
            List.of(
                    "system-to-reply",
                    "system-awaiting-reply",
                    "system-fyi",
                    "system-actioned",
                    "system-newsletter",
                    "system-marketing",
                    "system-calendar",
                    "system-receipt",
                    "system-notification",
                    "system-cold-email");

    public static final List<String> DEFAULT_RULE_TEMPLATE_KEYS_VI =
            DEFAULT_RULE_TEMPLATE_KEYS_EN.stream().map(key -> key + "-vi").toList();

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
        return materializeKeys(
                tenantId, onboardingService.selectedEnabledTemplateKeys(tenantId), false);
    }

    /**
     * Materializes the localized default rule set as ENABLED rules for a brand-new tenant
     * (first-login seeding). {@code "en"} picks {@link #DEFAULT_RULE_TEMPLATE_KEYS_EN}; anything
     * else (including {@code null}) falls back to Vietnamese ({@link
     * #DEFAULT_RULE_TEMPLATE_KEYS_VI}) — the app is Vietnamese-first. Idempotent per template key:
     * re-running skips any already-materialized key and preserves user-customized rules, so a
     * re-login or retry never duplicates or overwrites. Runs each key in its own {@code
     * REQUIRES_NEW} tenant-scoped transaction, so it is safe to call outside an ambient {@link
     * TenantContext}.
     */
    public RuleTemplateMaterializationResult materializeDefaultRulesEnabled(
            UUID tenantId, String language) {
        Objects.requireNonNull(tenantId, "tenantId");
        List<String> templateKeys =
                "en".equalsIgnoreCase(language)
                        ? DEFAULT_RULE_TEMPLATE_KEYS_EN
                        : DEFAULT_RULE_TEMPLATE_KEYS_VI;
        return materializeKeys(tenantId, templateKeys, true);
    }

    private RuleTemplateMaterializationResult materializeKeys(
            UUID tenantId, List<String> templateKeys, boolean enableOnCreate) {
        List<RuleStatusProjection> createdRules = new ArrayList<>();
        List<SkippedTemplate> skippedTemplates = new ArrayList<>();
        int customizedPreservedCount = 0;

        for (String templateKey : templateKeys) {
            TemplateMaterializationOutcome materializationOutcome =
                    materializeTemplateWithRetry(tenantId, templateKey, enableOnCreate);
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
                materializeTemplateWithRetry(tenantId, templateKey.trim(), false);
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
            UUID tenantId, String templateKey, boolean enableOnCreate) {
        try {
            return executeInTenantTransaction(
                    tenantId, () -> materializeTemplateOnce(tenantId, templateKey, enableOnCreate));
        } catch (DataIntegrityViolationException dataIntegrityViolation) {
            return executeInTenantTransaction(
                    tenantId,
                    () ->
                            reloadAfterConcurrentMaterialization(
                                    tenantId, templateKey, dataIntegrityViolation));
        }
    }

    private TemplateMaterializationOutcome materializeTemplateOnce(
            UUID tenantId, String templateKey, boolean enableOnCreate) {
        Optional<RuleEntity> existingRule =
                ruleRepository.findByTenantIdAndTemplateKey(tenantId, templateKey);
        if (existingRule.isPresent()) {
            return skippedExistingTemplateRule(
                    existingRule.get(), templateKey, SkippedTemplateReason.ALREADY_MATERIALIZED);
        }

        Optional<RuleTemplateEntity> template =
                ruleTemplateCatalogService.resolveLatestSeedableTemplate(templateKey);
        if (template.isEmpty()) {
            return TemplateMaterializationOutcome.skipped(
                    templateKey, SkippedTemplateReason.UNKNOWN_OR_DEPRECATED, false);
        }

        int orderIndex = (int) ruleRepository.countByTenantId(tenantId);
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
        if (enableOnCreate) {
            ruleEntity.setEnabled(true);
        }

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
