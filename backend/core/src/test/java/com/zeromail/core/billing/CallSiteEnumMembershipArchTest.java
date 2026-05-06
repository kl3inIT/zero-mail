package com.zeromail.core.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.zeromail.core.billing.model.CallSite;

class CallSiteEnumMembershipArchTest {

    @Test
    void callsite_has_exactly_three_members() {
        assertThat(CallSite.values()).hasSize(3);
    }

    @Test
    void callsite_members_locked_to_TRIAGE_DRAFT_PREVIEW() {
        assertThat(Arrays.stream(CallSite.values()).map(Enum::name))
                .containsExactlyInAnyOrder("TRIAGE", "DRAFT", "PREVIEW");
    }

    @Test
    void callsite_costs_match_spec() {
        assertThat(CallSite.TRIAGE.cost()).isEqualTo(1);
        assertThat(CallSite.DRAFT.cost()).isEqualTo(2);
        assertThat(CallSite.PREVIEW.cost()).isEqualTo(1);
    }
}
