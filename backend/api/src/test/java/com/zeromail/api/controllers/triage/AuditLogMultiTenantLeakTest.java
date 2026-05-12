package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class AuditLogMultiTenantLeakTest {

    private static final String AUDIT_LOG_QUERY_SERVICE =
            "com.zeromail.core.triage.projection.AuditLogQueryService";

    @Test
    void audit_log_query_filters_every_page_by_current_tenant() {
        Class<?> futureType = futureType();

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must ensure tenant A cannot observe tenant B triage audit rows on any cursor page");
    }

    private static Class<?> futureType() {
        try {
            return Class.forName(AUDIT_LOG_QUERY_SERVICE);
        } catch (ClassNotFoundException classNotFoundException) {
            fail(
                    "not implemented: " + AUDIT_LOG_QUERY_SERVICE + " missing",
                    classNotFoundException);
            throw new AssertionError("unreachable");
        }
    }
}
