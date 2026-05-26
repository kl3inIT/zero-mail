package com.zeromail.core.voice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class VoiceGenerationRateLimitTest {

    @Test
    @Disabled("Plan 09-05 Task 1 owns the generate-from-sent per-tenant rate-limit invariant")
    void fourthVoiceGenerationWithinOneHourIsRejected() {}
}
