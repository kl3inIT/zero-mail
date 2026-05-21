package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantListRow(
        UUID tenantId,
        Instant createdAt,
        String gmailAccountEmail,
        String status,
        String spendBucket7d) {

    public TenantListRow {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(spendBucket7d, "spendBucket7d must not be null");
    }
}
