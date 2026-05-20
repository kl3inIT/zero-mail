package com.zeromail.api.dto.admin.audit;

import com.zeromail.core.admin.audit.projection.AdminAuditPageQuery;
import java.time.Instant;
import java.util.UUID;

public record AdminAuditPageRequest(
        String actorEmail,
        String action,
        String targetKind,
        UUID targetId,
        Instant from,
        Instant to,
        String cursor,
        int limit) {

    public AdminAuditPageQuery toQuery() {
        return new AdminAuditPageQuery(
                limit, offset(), actorEmail, action, targetKind, targetId, from, to);
    }

    public String debounceKey() {
        return String.join(
                "|",
                value(actorEmail),
                value(action),
                value(targetKind),
                value(targetId),
                value(from),
                value(to),
                String.valueOf(limit));
    }

    public int nextOffset() {
        return offset() + toQuery().limit();
    }

    private int offset() {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException invalidCursor) {
            return 0;
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
