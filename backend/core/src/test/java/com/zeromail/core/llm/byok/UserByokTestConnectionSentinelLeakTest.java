package com.zeromail.core.llm.byok;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class UserByokTestConnectionSentinelLeakTest {

    @Test
    @Disabled("Plan 09-04 Task 2 owns the user BYOK provider-error sentinel leak invariant")
    void providerErrorBodiesNeverLeakThroughUserByokTestConnection() {}
}
