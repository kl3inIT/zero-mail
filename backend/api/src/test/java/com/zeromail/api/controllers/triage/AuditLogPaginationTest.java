package com.zeromail.api.controllers.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.shared.pagination.KeysetCursor;
import org.junit.jupiter.api.Test;

class AuditLogPaginationTest {

    @Test
    void audit_log_next_cursor_round_trips_with_full_instant_precision() {
        java.time.Instant fullPrecisionTimestamp =
                java.time.Instant.parse("2026-05-12T10:15:30.123456789Z");
        java.util.UUID auditId = java.util.UUID.randomUUID();

        String cursor = KeysetCursor.encode(fullPrecisionTimestamp, auditId);
        KeysetCursor decodedCursor = KeysetCursor.decode(cursor).orElseThrow();

        assertThat(decodedCursor.timestamp()).isEqualTo(fullPrecisionTimestamp);
        assertThat(decodedCursor.id()).isEqualTo(auditId.toString());
        assertThat(decodedCursor.isNullsLast()).isFalse();
    }
}
