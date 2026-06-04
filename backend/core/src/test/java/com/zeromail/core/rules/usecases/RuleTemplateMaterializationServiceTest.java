package com.zeromail.core.rules.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.rules.usecases.RuleTemplateMaterializationResult.SkippedTemplate;
import com.zeromail.core.rules.usecases.RuleTemplateMaterializationResult.SkippedTemplateReason;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class RuleTemplateMaterializationServiceTest extends PostgresContainerTest {

    private static final String ARCHIVE_RECEIPTS_KEY = "archive-receipts";
    private static final String LABEL_NEWSLETTERS_KEY = "label-newsletters";
    private static final String PIN_CALENDAR_KEY = "pin-calendar";

    @Autowired RuleTemplateMaterializationService ruleTemplateMaterializationService;

    @Autowired RuleRepository ruleRepository;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void selected_onboarding_templates_create_disabled_rules_exactly_once() throws Exception {
        UUID tenantId = seedTenant("template-materialization");
        insertSelection(tenantId, ARCHIVE_RECEIPTS_KEY, true);
        insertSelection(tenantId, LABEL_NEWSLETTERS_KEY, true);
        insertSelection(tenantId, PIN_CALENDAR_KEY, true);

        RuleTemplateMaterializationResult firstResult =
                ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId);
        RuleTemplateMaterializationResult secondResult =
                ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId);

        assertThat(firstResult.createdCount()).isEqualTo(3);
        assertThat(firstResult.skippedCount()).isZero();
        assertThat(secondResult.createdCount()).isZero();
        assertThat(secondResult.skippedCount()).isEqualTo(3);
        assertThat(withTenant(tenantId, () -> ruleRepository.findOrderedByTenantId(tenantId)))
                .extracting(
                        RuleEntity::getTemplateKey,
                        RuleEntity::isEnabled,
                        RuleEntity::getOrderIndex)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ARCHIVE_RECEIPTS_KEY, false, 0),
                        org.assertj.core.groups.Tuple.tuple(LABEL_NEWSLETTERS_KEY, false, 1),
                        org.assertj.core.groups.Tuple.tuple(PIN_CALENDAR_KEY, false, 2));
    }

    @Test
    void english_default_rules_are_materialized_enabled_exactly_once_on_first_login()
            throws Exception {
        UUID tenantId = seedTenant("default-rules-seed-en");

        RuleTemplateMaterializationResult firstResult =
                ruleTemplateMaterializationService.materializeDefaultRulesEnabled(tenantId, "en");
        RuleTemplateMaterializationResult secondResult =
                ruleTemplateMaterializationService.materializeDefaultRulesEnabled(tenantId, "en");

        int defaultCount = RuleTemplateMaterializationService.DEFAULT_RULE_TEMPLATE_KEYS_EN.size();
        assertThat(firstResult.createdCount()).isEqualTo(defaultCount);
        assertThat(secondResult.createdCount()).isZero();
        assertThat(secondResult.skippedCount()).isEqualTo(defaultCount);
        // Every seeded default rule is ENABLED, ordered by the EN key order, and carries valid
        // matcher/action JSON (getMatcherAst/getActionIntents validate on read).
        assertThat(withTenant(tenantId, () -> ruleRepository.findOrderedByTenantId(tenantId)))
                .allSatisfy(
                        rule -> {
                            assertThat(rule.isEnabled()).isTrue();
                            assertThat(rule.getMatcherAst()).contains("SEMANTIC_INTENT");
                            assertThat(rule.getActionIntents()).contains("label");
                        })
                .extracting(RuleEntity::getTemplateKey)
                .containsExactlyElementsOf(
                        RuleTemplateMaterializationService.DEFAULT_RULE_TEMPLATE_KEYS_EN);
    }

    @Test
    void vietnamese_default_rules_seed_localized_names_and_gmail_labels() throws Exception {
        UUID tenantId = seedTenant("default-rules-seed-vi");

        RuleTemplateMaterializationResult result =
                ruleTemplateMaterializationService.materializeDefaultRulesEnabled(tenantId, "vi");

        assertThat(result.createdCount())
                .isEqualTo(RuleTemplateMaterializationService.DEFAULT_RULE_TEMPLATE_KEYS_VI.size());
        var rules = withTenant(tenantId, () -> ruleRepository.findOrderedByTenantId(tenantId));
        assertThat(rules)
                .allSatisfy(rule -> assertThat(rule.isEnabled()).isTrue())
                .extracting(RuleEntity::getTemplateKey)
                .containsExactlyElementsOf(
                        RuleTemplateMaterializationService.DEFAULT_RULE_TEMPLATE_KEYS_VI);
        // The Vietnamese set localizes the rule name AND the Gmail label the user sees.
        assertThat(rules)
                .extracting(RuleEntity::getDisplayName)
                .contains("Cần trả lời", "Bản tin", "Tiếp thị", "Hóa đơn");
        assertThat(rules)
                .anySatisfy(
                        rule -> {
                            assertThat(rule.getDisplayName()).isEqualTo("Cần trả lời");
                            assertThat(rule.getActionIntents()).contains("Cần trả lời");
                        });
    }

    @Test
    void customized_template_derived_rule_is_preserved_on_repeated_materialization()
            throws Exception {
        UUID tenantId = seedTenant("template-materialization-customized");
        insertSelection(tenantId, ARCHIVE_RECEIPTS_KEY, true);
        ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId);
        jdbcTemplate.update(
                """
        update rules
        set customized = true,
            source_text = 'Custom user text'
        where tenant_id = ?
          and template_key = ?
        """,
                tenantId,
                ARCHIVE_RECEIPTS_KEY);

        RuleTemplateMaterializationResult repeatedResult =
                ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId);

        assertThat(repeatedResult.createdCount()).isZero();
        assertThat(repeatedResult.customizedPreservedCount()).isEqualTo(1);
        assertThat(repeatedResult.skippedTemplates())
                .extracting(SkippedTemplate::templateKey, SkippedTemplate::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                ARCHIVE_RECEIPTS_KEY, SkippedTemplateReason.CUSTOMIZED_PRESERVED));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select source_text from rules where tenant_id = ? and template_key = ?",
                                String.class,
                                tenantId,
                                ARCHIVE_RECEIPTS_KEY))
                .isEqualTo("Custom user text");
    }

    @Test
    void unknown_or_deprecated_selected_template_keys_are_reported_as_safe_skips() {
        UUID tenantId = seedTenant("template-materialization-skips");
        insertDeprecatedTemplate("deprecated-template");
        insertSelection(tenantId, "unknown-template", true);
        insertSelection(tenantId, "deprecated-template", true);

        RuleTemplateMaterializationResult result =
                ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId);

        assertThat(result.createdCount()).isZero();
        assertThat(result.skippedTemplates())
                .extracting(SkippedTemplate::templateKey, SkippedTemplate::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "deprecated-template", SkippedTemplateReason.UNKNOWN_OR_DEPRECATED),
                        org.assertj.core.groups.Tuple.tuple(
                                "unknown-template", SkippedTemplateReason.UNKNOWN_OR_DEPRECATED));
        assertThat(result.skippedTemplates().toString())
                .doesNotContain("matcher", "action", "source_text", "prompt", "completion");
    }

    @Test
    void concurrent_first_read_materialization_creates_at_most_one_rule_per_template()
            throws Exception {
        UUID tenantId = seedTenant("template-materialization-concurrent");
        insertSelection(tenantId, ARCHIVE_RECEIPTS_KEY, true);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<RuleTemplateMaterializationResult> firstFuture =
                    executorService.submit(
                            () -> materializeAfterLatch(tenantId, readyLatch, startLatch));
            Future<RuleTemplateMaterializationResult> secondFuture =
                    executorService.submit(
                            () -> materializeAfterLatch(tenantId, readyLatch, startLatch));

            readyLatch.await();
            startLatch.countDown();
            RuleTemplateMaterializationResult firstResult = firstFuture.get();
            RuleTemplateMaterializationResult secondResult = secondFuture.get();

            int createdTotal = firstResult.createdCount() + secondResult.createdCount();
            Integer persistedRules =
                    jdbcTemplate.queryForObject(
                            "select count(*) from rules where tenant_id = ? and template_key = ?",
                            Integer.class,
                            tenantId,
                            ARCHIVE_RECEIPTS_KEY);
            assertThat(createdTotal).isLessThanOrEqualTo(1);
            assertThat(persistedRules).isEqualTo(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void materialization_service_reads_onboarding_through_service_boundary() {
        assertThat(RuleTemplateMaterializationService.class.getDeclaredFields())
                .anySatisfy(
                        declaredField ->
                                assertThat(declaredField.getType())
                                        .isEqualTo(
                                                com.zeromail.core.onboarding.usecases
                                                        .OnboardingService.class));
        assertThat(RuleTemplateMaterializationService.class.getDeclaredFields())
                .noneSatisfy(
                        declaredField ->
                                assertThat(declaredField.getType().getName())
                                        .contains("com.zeromail.core.onboarding.persistence"));
    }

    private RuleTemplateMaterializationResult materializeAfterLatch(
            UUID tenantId, CountDownLatch readyLatch, CountDownLatch startLatch) throws Exception {
        readyLatch.countDown();
        startLatch.await();
        return ruleTemplateMaterializationService.materializeSelectedTemplates(tenantId);
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private void insertSelection(UUID tenantId, String templateKey, boolean enabled) {
        jdbcTemplate.update(
                "insert into onboarding_selections(id, tenant_id, template_key, enabled) values (?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                templateKey,
                enabled);
    }

    private void insertDeprecatedTemplate(String templateKey) {
        jdbcTemplate.update(
                """
        insert into rule_template_catalog(
          id, template_key, template_version, display_name, source_text, source_language,
          schema_version, matcher_ast, action_intents, status
        )
        values (?, ?, 1, 'Deprecated template', 'Deprecated template text', 'en', 'rules.v1',
          '{"schemaVersion":"rules.v1","type":"SENDER_DOMAIN","domain":"example.com"}'::jsonb,
          '[{"type":"archive"}]'::jsonb,
          'deprecated')
        """,
                UUID.randomUUID(),
                templateKey);
    }

    private <T> T withTenant(UUID tenantId, java.util.function.Supplier<T> supplier)
            throws Exception {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(supplier::get);
    }
}
