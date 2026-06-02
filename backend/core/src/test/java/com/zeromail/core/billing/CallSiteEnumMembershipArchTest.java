package com.zeromail.core.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CallSiteEnumMembershipArchTest {

    @Test
    void callsite_has_exactly_six_members() {
        assertThat(CallSite.values()).hasSize(6);
    }

    @Test
    void callsite_members_locked_to_known_billable_callsites() {
        assertThat(Arrays.stream(CallSite.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "TRIAGE",
                        "DRAFT",
                        "PREVIEW",
                        "TRIAGE_PLATFORM_LLM",
                        "TRIAGE_DETERMINISTIC",
                        "DIGEST");
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
