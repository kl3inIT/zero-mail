package com.zeromail.core.admin.audit.projection;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditPageQuery(
        int limit,
        int offset,
        String actorEmail,
        String action,
        String targetKind,
        UUID targetId,
        Instant since,
        Instant until) {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 10_000;

    public AdminAuditPageQuery {
        limit = clampLimit(limit);
        offset = Math.max(offset, 0);
        actorEmail = blankToNull(actorEmail);
        action = blankToNull(action);
        targetKind = blankToNull(targetKind);
    }

    public static AdminAuditPageQuery firstPage(int limit) {
        return new AdminAuditPageQuery(limit, 0, null, null, null, null, null, null);
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
