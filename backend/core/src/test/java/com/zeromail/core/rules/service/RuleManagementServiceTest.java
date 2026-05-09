package com.zeromail.core.rules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.rules.model.RuleCompileResult;
import com.zeromail.core.rules.model.RuleCreateCommand;
import com.zeromail.core.rules.model.RuleLanguage;
import com.zeromail.core.rules.model.RuleOrderEntry;
import com.zeromail.core.rules.model.RuleReorderCommand;
import com.zeromail.core.rules.model.RuleSchemaVersion;
import com.zeromail.core.rules.model.RuleStatusView;
import com.zeromail.core.rules.model.RuleUpdateCommand;
import com.zeromail.core.rules.model.RuleValidationException;
import com.zeromail.core.rules.persistence.RuleEntity;
import com.zeromail.core.rules.persistence.RuleRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

class RuleManagementServiceTest extends PostgresContainerTest {

  @Autowired RuleManagementService ruleManagementService;

  @Autowired RuleRepository ruleRepository;

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void new_rule_cannot_be_enabled_until_current_version_is_previewed() throws Exception {
    UUID tenantId = seedTenant("rules-enable-preview");
    RuleStatusView createdRule =
        withTenant(
            tenantId,
            () ->
                ruleManagementService.create(
                    createCommand(tenantId, "Archive receipts", "Archive Stripe receipts", null)));

    assertThat(createdRule.enabled()).isFalse();
    assertThat(createdRule.lastPreviewedEntityVersion()).isNull();
    assertThatThrownBy(() -> withTenant(tenantId, () -> ruleManagementService.enable(tenantId, createdRule.ruleId().value())))
        .isInstanceOf(RuleValidationException.class)
        .extracting("reason")
        .isEqualTo(RuleValidationException.Reason.PREVIEW_REQUIRED);

    RuleStatusView previewedRule =
        withTenant(
            tenantId,
            () ->
                ruleManagementService.markPreviewSucceeded(
                    tenantId,
                    createdRule.ruleId().value(),
                    createdRule.entityVersion(),
                    Instant.parse("2026-05-10T00:00:00Z")));
    RuleStatusView enabledRule =
        withTenant(tenantId, () -> ruleManagementService.enable(tenantId, createdRule.ruleId().value()));

    assertThat(previewedRule.lastPreviewedEntityVersion()).isEqualTo(createdRule.entityVersion());
    assertThat(enabledRule.enabled()).isTrue();
  }

  @Test
  void updating_enabled_rule_disables_it_clears_preview_and_marks_template_customized()
      throws Exception {
    UUID tenantId = seedTenant("rules-update-reset");
    RuleStatusView createdRule =
        withTenant(
            tenantId,
            () ->
                ruleManagementService.create(
                    createCommand(
                        tenantId,
                        "Archive receipts",
                        "Archive Stripe receipts",
                        "archive-receipts")));
    withTenant(
        tenantId,
        () ->
            ruleManagementService.markPreviewSucceeded(
                tenantId, createdRule.ruleId().value(), createdRule.entityVersion(), Instant.now()));
    withTenant(tenantId, () -> ruleManagementService.enable(tenantId, createdRule.ruleId().value()));

    RuleCompileResult updatedCompileResult =
        compiled(
            "Label Stripe receipts",
            "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}",
            "[{\"type\":\"label\",\"labelName\":\"Finance\"}]");
    withTenant(
        tenantId,
        () ->
            ruleManagementService.update(
                new RuleUpdateCommand(
                    tenantId,
                    createdRule.ruleId().value(),
                    "Label Stripe receipts",
                    "Label Stripe receipts as Finance",
                    updatedCompileResult)));

    RuleEntity updatedRule =
        withTenant(
            tenantId,
            () -> ruleRepository.findByIdAndTenantId(createdRule.ruleId().value(), tenantId).orElseThrow());

    assertThat(updatedRule.isEnabled()).isFalse();
    assertThat(updatedRule.getLastPreviewedEntityVersion()).isNull();
    assertThat(updatedRule.getLastPreviewedAt()).isNull();
    assertThat(updatedRule.isCustomized()).isTrue();
  }

  @Test
  void preview_enable_disable_and_reorder_do_not_mark_template_rule_customized() throws Exception {
    UUID tenantId = seedTenant("rules-customized-unchanged");
    RuleStatusView firstRule =
        withTenant(
            tenantId,
            () ->
                ruleManagementService.create(
                    createCommand(tenantId, "Archive receipts", "Archive Stripe receipts", "archive-receipts")));
    RuleStatusView secondRule =
        withTenant(
            tenantId,
            () ->
                ruleManagementService.create(
                    createCommand(tenantId, "Label newsletters", "Label newsletters", null)));

    withTenant(
        tenantId,
        () ->
            ruleManagementService.markPreviewSucceeded(
                tenantId, firstRule.ruleId().value(), firstRule.entityVersion(), Instant.now()));
    withTenant(tenantId, () -> ruleManagementService.enable(tenantId, firstRule.ruleId().value()));
    withTenant(tenantId, () -> ruleManagementService.disable(tenantId, firstRule.ruleId().value()));
    RuleStatusView currentFirstRule =
        withTenant(tenantId, () -> ruleManagementService.get(tenantId, firstRule.ruleId().value()));
    RuleStatusView currentSecondRule =
        withTenant(tenantId, () -> ruleManagementService.get(tenantId, secondRule.ruleId().value()));
    withTenant(
        tenantId,
        () ->
            ruleManagementService.reorder(
                new RuleReorderCommand(
                    tenantId,
                    List.of(
                        new RuleOrderEntry(currentSecondRule.ruleId().value(), currentSecondRule.entityVersion()),
                        new RuleOrderEntry(currentFirstRule.ruleId().value(), currentFirstRule.entityVersion())))));

    RuleEntity templateRule =
        withTenant(
            tenantId,
            () -> ruleRepository.findByIdAndTenantId(firstRule.ruleId().value(), tenantId).orElseThrow());
    assertThat(templateRule.isCustomized()).isFalse();
  }

  @Test
  void cross_tenant_get_update_delete_and_reorder_do_not_mutate_rows() throws Exception {
    UUID tenantAId = seedTenant("rules-tenant-a");
    UUID tenantBId = seedTenant("rules-tenant-b");
    RuleStatusView tenantARule =
        withTenant(
            tenantAId,
            () ->
                ruleManagementService.create(
                    createCommand(tenantAId, "Archive receipts", "Archive Stripe receipts", null)));

    assertThatThrownBy(() -> withTenant(tenantBId, () -> ruleManagementService.get(tenantBId, tenantARule.ruleId().value())))
        .isInstanceOf(RuleValidationException.class)
        .extracting("reason")
        .isEqualTo(RuleValidationException.Reason.NOT_FOUND);
    assertThatThrownBy(
            () ->
                withTenant(
                    tenantBId,
                    () ->
                        ruleManagementService.update(
                            new RuleUpdateCommand(
                                tenantBId,
                                tenantARule.ruleId().value(),
                                "Mutated",
                                "Mutated",
                                compiled("Mutated")))))
        .isInstanceOf(RuleValidationException.class);
    assertThatThrownBy(
            () ->
                withTenant(
                    tenantBId,
                    () ->
                        ruleManagementService.reorder(
                            new RuleReorderCommand(
                                tenantBId,
                                List.of(new RuleOrderEntry(tenantARule.ruleId().value(), tenantARule.entityVersion()))))))
        .isInstanceOf(RuleValidationException.class);
    assertThatThrownBy(
            () -> withTenantVoid(tenantBId, () -> ruleManagementService.delete(tenantBId, tenantARule.ruleId().value())))
        .isInstanceOf(RuleValidationException.class);

    RuleStatusView unchangedRule =
        withTenant(tenantAId, () -> ruleManagementService.get(tenantAId, tenantARule.ruleId().value()));
    assertThat(unchangedRule.displayName()).isEqualTo("Archive receipts");
    assertThat(unchangedRule.orderIndex()).isZero();
  }

  @Test
  void reorder_is_version_checked_all_or_nothing_and_delete_normalizes_order() throws Exception {
    UUID tenantId = seedTenant("rules-reorder");
    RuleStatusView firstRule =
        withTenant(
            tenantId,
            () -> ruleManagementService.create(createCommand(tenantId, "First rule", "First rule", null)));
    RuleStatusView secondRule =
        withTenant(
            tenantId,
            () -> ruleManagementService.create(createCommand(tenantId, "Second rule", "Second rule", null)));
    RuleStatusView thirdRule =
        withTenant(
            tenantId,
            () -> ruleManagementService.create(createCommand(tenantId, "Third rule", "Third rule", null)));

    assertThatThrownBy(
            () ->
                withTenant(
                    tenantId,
                    () ->
                        ruleManagementService.reorder(
                            new RuleReorderCommand(
                                tenantId,
                                List.of(
                                    new RuleOrderEntry(thirdRule.ruleId().value(), thirdRule.entityVersion()),
                                    new RuleOrderEntry(secondRule.ruleId().value(), secondRule.entityVersion() + 1),
                                    new RuleOrderEntry(firstRule.ruleId().value(), firstRule.entityVersion()))))))
        .isInstanceOf(RuleValidationException.class)
        .extracting("reason")
        .isEqualTo(RuleValidationException.Reason.VERSION_MISMATCH);
    assertThat(withTenant(tenantId, () -> ruleManagementService.listOrdered(tenantId)))
        .extracting(RuleStatusView::displayName)
        .containsExactly("First rule", "Second rule", "Third rule");

    RuleStatusView currentFirstRule =
        withTenant(tenantId, () -> ruleManagementService.get(tenantId, firstRule.ruleId().value()));
    RuleStatusView currentSecondRule =
        withTenant(tenantId, () -> ruleManagementService.get(tenantId, secondRule.ruleId().value()));
    RuleStatusView currentThirdRule =
        withTenant(tenantId, () -> ruleManagementService.get(tenantId, thirdRule.ruleId().value()));
    withTenant(
        tenantId,
        () ->
            ruleManagementService.reorder(
                new RuleReorderCommand(
                    tenantId,
                    List.of(
                        new RuleOrderEntry(currentThirdRule.ruleId().value(), currentThirdRule.entityVersion()),
                        new RuleOrderEntry(currentSecondRule.ruleId().value(), currentSecondRule.entityVersion()),
                        new RuleOrderEntry(currentFirstRule.ruleId().value(), currentFirstRule.entityVersion())))));

    assertThat(withTenant(tenantId, () -> ruleManagementService.listOrdered(tenantId)))
        .extracting(RuleStatusView::displayName, RuleStatusView::orderIndex)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("Third rule", 0),
            org.assertj.core.groups.Tuple.tuple("Second rule", 1),
            org.assertj.core.groups.Tuple.tuple("First rule", 2));

    withTenantVoid(tenantId, () -> ruleManagementService.delete(tenantId, currentSecondRule.ruleId().value()));

    assertThat(withTenant(tenantId, () -> ruleManagementService.listOrdered(tenantId)))
        .extracting(RuleStatusView::orderIndex)
        .containsExactly(0, 1);
  }

  private UUID seedTenant(String displayName) {
    UUID tenantId = UUID.randomUUID();
    jdbcTemplate.update("insert into tenants(id, display_name) values (?, ?)", tenantId, displayName);
    return tenantId;
  }

  private RuleCreateCommand createCommand(
      UUID tenantId, String displayName, String sourceText, String templateKey) {
    return new RuleCreateCommand(
        UUID.randomUUID(),
        tenantId,
        displayName,
        sourceText,
        compiled(displayName),
        templateKey,
        templateKey == null ? null : 1);
  }

  private RuleCompileResult compiled(String displayName) {
    return compiled(
        displayName,
        "{\"schemaVersion\":\"rules.v1\",\"type\":\"SENDER_DOMAIN\",\"domain\":\"stripe.com\"}",
        "[{\"type\":\"archive\"}]");
  }

  private RuleCompileResult compiled(String displayName, String matcherAst, String actionIntents) {
    return RuleCompileResult.compiled(
        RuleLanguage.EN, displayName, RuleSchemaVersion.RULES_V1, matcherAst, actionIntents);
  }

  private <T> T withTenant(UUID tenantId, Supplier<T> supplier) throws Exception {
    return ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(supplier::get);
  }

  private void withTenantVoid(UUID tenantId, Runnable operation) {
    ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(operation);
  }
}
