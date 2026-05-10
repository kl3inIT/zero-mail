package com.zeromail.core.rules.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.zeromail.core.onboarding.service.OnboardingService;
import com.zeromail.core.rules.application.RuleTemplateMaterializationResult;

class RuleTemplateMaterializationWave0Test {

  @Test
  void materialization_service_exposes_actual_plan_03_06_entrypoint() throws Exception {
    Method materializeMethod =
        RuleTemplateMaterializationService.class.getMethod("materializeSelectedTemplates", UUID.class);

    assertThat(materializeMethod.getReturnType()).isEqualTo(RuleTemplateMaterializationResult.class);
  }

  @Test
  void materialization_result_reports_created_skipped_and_customized_counts() throws Exception {
    assertThat(RuleTemplateMaterializationResult.class.getMethod("createdCount")).isNotNull();
    assertThat(RuleTemplateMaterializationResult.class.getMethod("skippedCount")).isNotNull();
    assertThat(RuleTemplateMaterializationResult.class.getMethod("customizedPreservedCount"))
        .isNotNull();
    assertThat(RuleTemplateMaterializationResult.class.getMethod("skippedTemplates")).isNotNull();
  }

  @Test
  void materialization_reads_onboarding_selections_through_onboarding_service_not_repository() {
    assertThat(RuleTemplateMaterializationService.class.getDeclaredFields())
        .anySatisfy(
            declaredField -> assertThat(declaredField.getType()).isEqualTo(OnboardingService.class));
    assertThat(RuleTemplateMaterializationService.class.getDeclaredFields())
        .noneSatisfy(
            declaredField ->
                assertThat(declaredField.getType().getName())
                    .contains("com.zeromail.core.onboarding.persistence"));
  }
}
