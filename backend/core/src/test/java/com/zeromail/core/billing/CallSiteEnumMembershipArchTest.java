package com.zeromail.core.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CallSiteEnumMembershipArchTest {

    @Test
    void callsite_has_exactly_five_members() {
        assertThat(CallSite.values()).hasSize(5);
    }

    @Test
    void callsite_members_locked_to_TRIAGE_DRAFT_PREVIEW_AND_TRIAGE_EXECUTION_CALLSITES() {
        assertThat(Arrays.stream(CallSite.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "TRIAGE",
                        "DRAFT",
                        "PREVIEW",
                        "TRIAGE_PLATFORM_LLM",
                        "TRIAGE_DETERMINISTIC");
    }

    @Test
    void callsite_id_equals_enum_name_for_every_member() {
        // The id() value is the FK into feature_catalog(code). Drift between enum name and id()
        // would break FeatureCatalogConsistencyChecker startup validation and silently route DB
        // lookups to the wrong rows.
        for (CallSite callSite : CallSite.values()) {
            assertThat(callSite.id()).isEqualTo(callSite.name());
        }
    }
}
