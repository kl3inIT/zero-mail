package com.zeromail.core.rules.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class RuleTemplateMaterializationWave0Test {

  private static final String PLAN_03_06_TEMPLATE_MESSAGE =
      "Plan 03-06 lands template catalog and materialization service";

  @Test
  @Disabled(PLAN_03_06_TEMPLATE_MESSAGE)
  void selected_onboarding_template_keys_create_disabled_rules_exactly_once() throws Exception {
    Object materializationService = newFutureMaterializationService();
    UUID tenantId = UUID.randomUUID();
    List<String> selectedTemplateKeys =
        List.of("archive-receipts", "label-newsletters", "pin-calendar");

    Object firstResult = materialize(materializationService, tenantId, selectedTemplateKeys);
    Object secondResult = materialize(materializationService, tenantId, selectedTemplateKeys);

    assertThat(createdCount(firstResult)).isEqualTo(3);
    assertThat(createdCount(secondResult)).isZero();
    assertThat(allCreatedRulesDisabled(firstResult)).isTrue();
  }

  @Test
  @Disabled(PLAN_03_06_TEMPLATE_MESSAGE)
  void customized_template_derived_rules_are_preserved_on_repeated_materialization()
      throws Exception {
    Object materializationService = newFutureMaterializationService();
    UUID tenantId = UUID.randomUUID();

    materialize(materializationService, tenantId, List.of("archive-receipts"));
    markTemplateRuleCustomized(materializationService, tenantId, "archive-receipts");
    Object repeatedResult = materialize(materializationService, tenantId, List.of("archive-receipts"));

    assertThat(createdCount(repeatedResult)).isZero();
    assertThat(customizedPreservedCount(repeatedResult)).isEqualTo(1);
  }

  @Test
  @Disabled(PLAN_03_06_TEMPLATE_MESSAGE)
  void materialization_reads_onboarding_selections_through_onboarding_service_not_repository()
      throws Exception {
    Class<?> materializationServiceClass =
        Class.forName("com.zeromail.core.rules.service.RuleTemplateMaterializationService");

    assertThat(materializationServiceClass.getDeclaredFields())
        .anySatisfy(
            declaredField ->
                assertThat(declaredField.getType().getName())
                    .isEqualTo("com.zeromail.core.onboarding.service.OnboardingService"));
    assertThat(materializationServiceClass.getDeclaredFields())
        .noneSatisfy(
            declaredField ->
                assertThat(declaredField.getType().getName())
                    .contains("com.zeromail.core.onboarding.persistence.OnboardingSelectionRepository"));
  }

  private static Object newFutureMaterializationService() throws Exception {
    return Class.forName("com.zeromail.core.rules.service.RuleTemplateMaterializationService")
        .getConstructor()
        .newInstance();
  }

  private static Object materialize(
      Object materializationService, UUID tenantId, List<String> selectedTemplateKeys)
      throws Exception {
    Method materializeMethod =
        materializationService
            .getClass()
            .getMethod("materializeSelectedTemplates", UUID.class, List.class);
    return materializeMethod.invoke(materializationService, tenantId, selectedTemplateKeys);
  }

  private static void markTemplateRuleCustomized(
      Object materializationService, UUID tenantId, String templateKey) throws Exception {
    Method markCustomizedMethod =
        materializationService
            .getClass()
            .getMethod("markTemplateRuleCustomizedForTest", UUID.class, String.class);
    markCustomizedMethod.invoke(materializationService, tenantId, templateKey);
  }

  private static int createdCount(Object materializationResult) throws Exception {
    Method createdCountMethod = materializationResult.getClass().getMethod("createdCount");
    return (Integer) createdCountMethod.invoke(materializationResult);
  }

  private static boolean allCreatedRulesDisabled(Object materializationResult) throws Exception {
    Method rulesMethod = materializationResult.getClass().getMethod("createdRules");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> createdRules =
        (List<Map<String, Object>>) rulesMethod.invoke(materializationResult);
    return createdRules.stream()
        .allMatch(createdRule -> Boolean.FALSE.equals(createdRule.get("enabled")));
  }

  private static int customizedPreservedCount(Object materializationResult) throws Exception {
    Method customizedPreservedCountMethod =
        materializationResult.getClass().getMethod("customizedPreservedCount");
    return (Integer) customizedPreservedCountMethod.invoke(materializationResult);
  }
}
