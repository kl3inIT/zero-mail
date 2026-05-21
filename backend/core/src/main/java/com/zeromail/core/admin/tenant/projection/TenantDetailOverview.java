package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantDetailOverview(
        UUID tenantId,
        Instant createdAt,
        String gmailAccountEmail,
        String status,
        Instant lastActivityAt,
        int rulesCount) {

    public TenantDetailOverview {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
