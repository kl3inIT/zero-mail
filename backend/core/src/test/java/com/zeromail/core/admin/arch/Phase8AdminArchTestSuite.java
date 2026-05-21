package com.zeromail.core.admin.arch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Phase8AdminArchTestSuite {

    @Test
    void phase8_admin_arch_rules_have_suite_anchor() {
        assertThat(AdminContextMutexTest.class).isNotNull();
        assertThat(AdminPathBodyBanTest.class).isNotNull();
        assertThat(AdminSendBanTest.class).isNotNull();
        assertThat(AdminTenantOAuthGuardTest.class).isNotNull();
        assertThat(MasterKeyResolverConfinementTest.class).isNotNull();
        assertThat(MasterKeySentinelLeakTest.class).isNotNull();
        assertThat(OnlyBodyBanFilterCanCallAppendAsSystem.class).isNotNull();
    }
}
