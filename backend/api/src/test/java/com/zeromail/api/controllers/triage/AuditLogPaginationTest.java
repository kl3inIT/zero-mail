package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class AuditLogPaginationTest {

    private static final String AUDIT_LOG_QUERY_SERVICE =
            "com.zeromail.core.triage.projection.AuditLogQueryService";

    @Test
    void audit_log_next_cursor_round_trips_with_full_instant_precision() {
        Class<?> futureType = futureType();

        fail(
                "not implemented: "
                        + futureType.getName()
                        + " must keyset paginate by full-precision (createdAt, auditId), not offset or "
                        + "millisecond-truncated cursors");
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
