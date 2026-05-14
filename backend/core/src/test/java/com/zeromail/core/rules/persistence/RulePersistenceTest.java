package com.zeromail.core.rules.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.domain.RuleTemplateStatus;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class RulePersistenceTest extends PostgresContainerTest {

    private static final String ARCHIVE_RECEIPTS_KEY = "archive-receipts";
    private static final String LABEL_NEWSLETTERS_KEY = "label-newsletters";
    private static final String PIN_CALENDAR_KEY = "pin-calendar";
    private static final String CALENDAR_INVITES_KEY = "calendar-invites";

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired RuleRepository ruleRepository;

    @Autowired RuleTemplateRepository ruleTemplateRepository;

    @Test
    void rule_entity_defaults_disabled_and_round_trips_jsonb_payloads() {
        UUID tenantId = seedTenant("rules-roundtrip");
        RuleEntity ruleEntity =
                new RuleEntity(
                        UUID.randomUUID(),
                        tenantId,
                        "Archive receipts",
                        "Archive Stripe receipts",
                        RuleLanguage.EN,
                        RuleSchemaVersion.RULES_V1,
                        """
            {"schemaVersion":"rules.v1","type":"SENDER_DOMAIN","domain":"stripe.com"}
            """,
                        """
            [{"type":"label","labelName":"Receipts"},{"type":"archive"}]
            """,
                        10,
                        ARCHIVE_RECEIPTS_KEY,
                        1);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> ruleRepository.saveAndFlush(ruleEntity));

        RuleEntity foundRule =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        ruleRepository
                                                .findByIdAndTenantId(ruleEntity.getId(), tenantId)
                                                .orElseThrow());

        assertThat(foundRule.isEnabled()).isFalse();
        assertThat(foundRule.getMatcherAst()).contains("\"schemaVersion\"", "\"rules.v1\"");
        assertThat(foundRule.getActionIntents()).contains("\"type\"", "\"archive\"");
        assertThat(foundRule.getSchemaVersion()).isEqualTo(RuleSchemaVersion.RULES_V1);
        assertThat(foundRule.getEntityVersion()).isNotNull();
        assertThat(foundRule.getLastPreviewedEntityVersion()).isNull();
    }

    @Test
    void last_previewed_entity_version_is_distinct_from_schema_and_entity_version() {
        UUID tenantId = seedTenant("rules-preview-version");
        RuleEntity ruleEntity =
                new RuleEntity(
                        UUID.randomUUID(),
                        tenantId,
                        "Label newsletters",
                        "Label newsletters",
                        RuleLanguage.EN,
                        RuleSchemaVersion.RULES_V1,
                        """
            {"schemaVersion":"rules.v1","type":"NEWSLETTER_INDICATOR"}
            """,
                        """
            [{"type":"label","labelName":"Newsletters"}]
            """,
                        20,
                        null,
                        null);
        ruleEntity.markPreviewed(42, Instant.parse("2026-05-10T00:00:00Z"));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> ruleRepository.saveAndFlush(ruleEntity));

        RuleEntity foundRule =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        ruleRepository
                                                .findByIdAndTenantId(ruleEntity.getId(), tenantId)
                                                .orElseThrow());

        assertThat(foundRule.getEntityVersion())
                .isNotEqualTo(foundRule.getLastPreviewedEntityVersion());
        assertThat(foundRule.getLastPreviewedEntityVersion()).isEqualTo(42);
        assertThat(foundRule.getSchemaVersion().id()).isEqualTo("rules.v1");
    }

    @Test
    void mandatory_indexes_and_partial_template_key_uniqueness_are_present() {
        List<String> indexNames =
                jdbcTemplate.queryForList(
                        """
            select indexname
            from pg_indexes
            where tablename in ('rules', 'rule_template_catalog')
            """,
                        String.class);

        assertThat(indexNames)
                .contains(
                        "idx_rules_tenant_order",
                        "idx_rules_tenant_enabled_order",
                        "uq_rules_tenant_template_key_present",
                        "idx_rules_matcher_ast_gin",
                        "idx_rules_action_intents_gin",
                        "idx_rule_template_catalog_matcher_ast_gin",
                        "idx_rule_template_catalog_action_intents_gin");

        String matcherIndexDefinition = indexDefinition("idx_rules_matcher_ast_gin");
        String partialUniqueIndexDefinition =
                indexDefinition("uq_rules_tenant_template_key_present");
        assertThat(matcherIndexDefinition).contains("jsonb_path_ops");
        assertThat(partialUniqueIndexDefinition).contains("WHERE (template_key IS NOT NULL)");
    }

    @Test
    void partial_unique_template_key_blocks_duplicate_materialization_for_same_tenant() {
        UUID tenantId = seedTenant("rules-template-unique");
        insertRuleWithTemplateKey(UUID.randomUUID(), tenantId, ARCHIVE_RECEIPTS_KEY, 0);

        assertThatThrownBy(
                        () ->
                                insertRuleWithTemplateKey(
                                        UUID.randomUUID(), tenantId, ARCHIVE_RECEIPTS_KEY, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void template_seed_rows_are_present_once_and_onboarding_keys_are_materializable() {
        List<String> materializableKeys =
                ruleTemplateRepository
                        .findByStatusIdOrderByTemplateKeyAscTemplateVersionAsc(
                                RuleTemplateStatus.MATERIALIZABLE.id())
                        .stream()
                        .map(RuleTemplateEntity::getTemplateKey)
                        .toList();
        List<String> galleryOnlyKeys =
                ruleTemplateRepository
                        .findByStatusIdOrderByTemplateKeyAscTemplateVersionAsc(
                                RuleTemplateStatus.GALLERY_ONLY.id())
                        .stream()
                        .map(RuleTemplateEntity::getTemplateKey)
                        .toList();

        assertThat(materializableKeys)
                .containsExactly(ARCHIVE_RECEIPTS_KEY, LABEL_NEWSLETTERS_KEY, PIN_CALENDAR_KEY);
        assertThat(galleryOnlyKeys).containsExactly(CALENDAR_INVITES_KEY);

        Integer duplicateTemplateRows =
                jdbcTemplate.queryForObject(
                        """
            select count(*)
            from (
              select template_key, template_version
              from rule_template_catalog
              group by template_key, template_version
              having count(*) > 1
            ) duplicates
            """,
                        Integer.class);
        assertThat(duplicateTemplateRows).isZero();
    }

    @Test
    void schema_excludes_raw_email_content_columns() {
        assertThat(columnExists("rules", "raw_body")).isFalse();
        assertThat(columnExists("rules", "snippet")).isFalse();
        assertThat(columnExists("rules", "prompt")).isFalse();
        assertThat(columnExists("rules", "completion")).isFalse();
    }

    @Test
    void onboarding_template_keys_align_with_materializable_catalog_keys() {
        UUID tenantId = seedTenant("rules-onboarding-alignment");
        insertOnboardingSelection(UUID.randomUUID(), tenantId, ARCHIVE_RECEIPTS_KEY);
        insertOnboardingSelection(UUID.randomUUID(), tenantId, LABEL_NEWSLETTERS_KEY);
        insertOnboardingSelection(UUID.randomUUID(), tenantId, PIN_CALENDAR_KEY);

        List<String> missingMaterializableKeys =
                jdbcTemplate.queryForList(
                        """
            select onboarding_selection.template_key
            from onboarding_selections onboarding_selection
            left join rule_template_catalog rule_template
              on rule_template.template_key = onboarding_selection.template_key
             and rule_template.status = 'materializable'
            where onboarding_selection.tenant_id = ?
              and onboarding_selection.enabled = true
              and rule_template.template_key is null
            order by onboarding_selection.template_key
            """,
                        String.class,
                        tenantId);

        assertThat(missingMaterializableKeys).isEmpty();
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = ?", String.class, indexName);
    }

    private void insertRuleWithTemplateKey(
            UUID ruleId, UUID tenantId, String templateKey, int orderIndex) {
        jdbcTemplate.update(
                """
        insert into rules(
          id, tenant_id, display_name, source_text, source_language, schema_version,
          matcher_ast, action_intents, order_index, template_key, template_version
        )
        values (?, ?, 'Archive receipts', 'Archive receipts', 'en', 'rules.v1',
          '{"schemaVersion":"rules.v1","type":"SENDER_DOMAIN","domain":"stripe.com"}'::jsonb,
          '[{"type":"archive"}]'::jsonb, ?, ?, 1)
        """,
                ruleId,
                tenantId,
                orderIndex,
                templateKey);
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer columnCount =
                jdbcTemplate.queryForObject(
                        """
            select count(*)
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = ?
              and column_name = ?
            """,
                        Integer.class,
                        tableName,
                        columnName);
        return columnCount != null && columnCount > 0;
    }

    private void insertOnboardingSelection(UUID selectionId, UUID tenantId, String templateKey) {
        jdbcTemplate.update(
                """
        insert into onboarding_selections(id, tenant_id, template_key, enabled)
        values (?, ?, ?, true)
        """,
                selectionId,
                tenantId,
                templateKey);
    }
}
