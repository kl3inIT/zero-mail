package com.zeromail.core.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.zeromail.core.billing.domain.CallSite;

class CallSiteEnumMembershipArchTest {

  @Test
  void callsite_has_exactly_five_members() {
    assertThat(CallSite.values()).hasSize(5);
  }

  @Test
  void callsite_members_locked_to_TRIAGE_DRAFT_PREVIEW_AND_TRIAGE_EXECUTION_CALLSITES() {
    assertThat(Arrays.stream(CallSite.values()).map(Enum::name))
        .containsExactlyInAnyOrder(
            "TRIAGE", "DRAFT", "PREVIEW", "TRIAGE_PLATFORM_LLM", "TRIAGE_DETERMINISTIC");
  }

  @Test
  void callsite_costs_match_spec() {
    assertThat(CallSite.TRIAGE.cost()).isEqualTo(1);
    assertThat(CallSite.DRAFT.cost()).isEqualTo(2);
    assertThat(CallSite.PREVIEW.cost()).isEqualTo(1);
    assertThat(CallSite.TRIAGE_PLATFORM_LLM.cost()).isEqualTo(1);
    assertThat(CallSite.TRIAGE_DETERMINISTIC.cost()).isZero();
  }
}
