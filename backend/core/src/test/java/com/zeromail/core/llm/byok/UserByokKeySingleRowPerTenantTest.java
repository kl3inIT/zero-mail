package com.zeromail.core.llm.byok;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class UserByokKeySingleRowPerTenantTest {

    @Test
    @Disabled("Plan 09-04 Task 2 owns the single user_byok_key row per tenant invariant")
    void savingNewProviderKeepsExactlyOneRowPerTenant() {}
}
