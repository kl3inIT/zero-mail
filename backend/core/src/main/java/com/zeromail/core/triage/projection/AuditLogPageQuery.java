package com.zeromail.core.triage.projection;

import java.time.Instant;

public record AuditLogPageQuery(
        int limit, String cursor, String action, Instant since, Instant until) {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    public AuditLogPageQuery {
        limit = clampLimit(limit);
        cursor = blankToNull(cursor);
        action = blankToNull(action);
    }

    private static int clampLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
