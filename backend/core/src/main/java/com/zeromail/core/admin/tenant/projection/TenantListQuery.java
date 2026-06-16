package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.Locale;

public record TenantListQuery(
        int limit, int offset, String status, Instant from, Instant to, String email) {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    public TenantListQuery {
        limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        offset = Math.max(offset, 0);
        status = normalizeStatus(status);
        email = normalizeEmail(email);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }
}
