package com.zeromail.api.admin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
@Disabled("Enabled in Task 8A-04 once the admin SecurityFilterChain and session cookies exist.")
class AdminChainCookieIsolationTest {

    @Test
    void user_session_cookie_cannot_access_admin_endpoints() {
        throw new AssertionError("Task 8A-04 must wire this integration assertion.");
    }

    @Test
    void unauthenticated_admin_request_returns_unauthorized_at_chain_level() {
        throw new AssertionError("Task 8A-04 must wire this integration assertion.");
    }
}
