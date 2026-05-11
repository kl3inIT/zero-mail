package com.zeromail.core.rules.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
import com.zeromail.core.rules.domain.RuleTemplateStatus;
import com.zeromail.core.rules.projection.RuleTemplateProjection;
import com.zeromail.core.rules.usecases.RuleTemplateCatalogService;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RuleTemplateCatalogTest extends PostgresContainerTest {

    private static final String ARCHIVE_RECEIPTS_KEY = "archive-receipts";
    private static final String LABEL_NEWSLETTERS_KEY = "label-newsletters";
    private static final String PIN_CALENDAR_KEY = "pin-calendar";
    private static final String CALENDAR_INVITES_KEY = "calendar-invites";

    @Autowired RuleTemplateCatalogService ruleTemplateCatalogService;

    @Autowired RuleRepository ruleRepository;

    @Autowired RuleTemplateRepository ruleTemplateRepository;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void db_backed_catalog_lists_active_templates_with_onboarding_source_metadata()
            throws Exception {
        UUID tenantId = seedTenant("template-catalog-list");

        List<RuleTemplateProjection> templateViews =
                withTenant(
                        tenantId, () -> ruleTemplateCatalogService.listActiveTemplates(tenantId));

        assertThat(templateViews)
                .extracting(RuleTemplateProjection::templateKey)
                .containsExactly(
                        ARCHIVE_RECEIPTS_KEY,
                        LABEL_NEWSLETTERS_KEY,
                        PIN_CALENDAR_KEY,
                        CALENDAR_INVITES_KEY);
        assertThat(templateViews)
                .filteredOn(RuleTemplateProjection::sourcedFromOnboarding)
                .extracting(RuleTemplateProjection::templateKey)
                .containsExactly(ARCHIVE_RECEIPTS_KEY, LABEL_NEWSLETTERS_KEY, PIN_CALENDAR_KEY);
        assertThat(templateViews)
                .filteredOn(templateView -> !templateView.sourcedFromOnboarding())
                .singleElement()
                .satisfies(
                        templateView -> {
                            assertThat(templateView.templateKey()).isEqualTo(CALENDAR_INVITES_KEY);
                            assertThat(templateView.status())
                                    .isEqualTo(RuleTemplateStatus.GALLERY_ONLY);
                        });
    }

    @Test
    void catalog_views_do_not_expose_matcher_json_action_json_raw_gmail_content_or_prompts()
            throws Exception {
        UUID tenantId = seedTenant("template-catalog-safe");

        List<RuleTemplateProjection> templateViews =
                withTenant(
                        tenantId, () -> ruleTemplateCatalogService.listActiveTemplates(tenantId));

        assertThat(templateViews).isNotEmpty();
        for (RuleTemplateProjection templateView : templateViews) {
            assertThat(templateView.localizedCopyKey()).startsWith("rules.templates.");
            assertThat(templateView.actionSummary())
                    .doesNotContain("{", "}", "matcher", "prompt", "completion");
            assertThat(templateView.toString())
                    .doesNotContain(
                            "matcherAst",
                            "actionIntents",
                            "From:",
                            "Subject:",
                            "prompt",
                            "completion");
        }
    }

    @Test
    void catalog_view_marks_tenant_materialized_and_customized_state() throws Exception {
        UUID tenantId = seedTenant("template-catalog-materialized");
        RuleEntity ruleEntity =
                new RuleEntity(
                        UUID.randomUUID(),
                        tenantId,
                        "Archive receipts",
                        "Archive receipts",
                        RuleLanguage.EN,
                        RuleSchemaVersion.RULES_V1,
                        "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}",
                        "[{\"type\":\"archive\"}]",
                        0,
                        ARCHIVE_RECEIPTS_KEY,
                        1);
        ruleEntity.markCustomized();
        withTenant(tenantId, () -> ruleRepository.saveAndFlush(ruleEntity));

        RuleTemplateProjection archiveTemplate =
                withTenant(tenantId, () -> ruleTemplateCatalogService.listActiveTemplates(tenantId))
                        .stream()
                        .filter(
                                templateView ->
                                        ARCHIVE_RECEIPTS_KEY.equals(templateView.templateKey()))
                        .findFirst()
                        .orElseThrow();

        assertThat(archiveTemplate.materialized()).isTrue();
        assertThat(archiveTemplate.customized()).isTrue();
    }

    @Test
    void resolves_specific_or_latest_active_template_version() throws Exception {
        UUID tenantId = seedTenant("template-catalog-resolve");
        RuleTemplateEntity versionTwoTemplate =
                new RuleTemplateEntity(
                        UUID.randomUUID(),
                        ARCHIVE_RECEIPTS_KEY,
                        2,
                        "Archive receipts v2",
                        "Archive receipts from stores.",
                        RuleLanguage.EN,
                        RuleSchemaVersion.RULES_V1,
                        "{\"schemaVersion\":\"rules.v1\",\"type\":\"SUBJECT_CONTAINS\",\"text\":\"receipt\"}",
                        "[{\"type\":\"archive\"}]",
                        RuleTemplateStatus.MATERIALIZABLE);
        withTenant(tenantId, () -> ruleTemplateRepository.saveAndFlush(versionTwoTemplate));

        assertThat(ruleTemplateCatalogService.resolveTemplate(ARCHIVE_RECEIPTS_KEY, 1))
                .get()
                .extracting(RuleTemplateEntity::getTemplateVersion)
                .isEqualTo(1);
        assertThat(ruleTemplateCatalogService.resolveTemplate(ARCHIVE_RECEIPTS_KEY, null))
                .get()
                .extracting(RuleTemplateEntity::getTemplateVersion)
                .isEqualTo(2);
    }

    @Test
    void seeded_materializable_rows_cover_onboarding_template_keys() {
        List<String> materializableKeys =
                ruleTemplateRepository
                        .findByStatusIdOrderByTemplateKeyAscTemplateVersionAsc(
                                RuleTemplateStatus.MATERIALIZABLE.id())
                        .stream()
                        .map(RuleTemplateEntity::getTemplateKey)
                        .toList();

        assertThat(materializableKeys)
                .containsExactly(ARCHIVE_RECEIPTS_KEY, LABEL_NEWSLETTERS_KEY, PIN_CALENDAR_KEY);
        assertThat(
                        ruleTemplateRepository
                                .findByStatusIdOrderByTemplateKeyAscTemplateVersionAsc(
                                        RuleTemplateStatus.GALLERY_ONLY.id()))
                .extracting(RuleTemplateEntity::getTemplateKey)
                .containsExactly(CALENDAR_INVITES_KEY);
    }

    private UUID seedTenant(String displayName) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
        return tenantId;
    }

    private <T> T withTenant(UUID tenantId, java.util.function.Supplier<T> supplier)
            throws Exception {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(supplier::get);
    }
}
