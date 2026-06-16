package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TenantDetailOverview(
        UUID tenantId,
        Instant createdAt,
        String gmailAccountEmail,
        String status,
        String gmailConnectionStatus,
        String telegramStatus,
        Instant lastActivityAt,
        int rulesCount,
        int enabledRulesCount,
        List<String> enabledRuleNames) {

    public TenantDetailOverview {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(gmailConnectionStatus, "gmailConnectionStatus must not be null");
        Objects.requireNonNull(telegramStatus, "telegramStatus must not be null");
        enabledRuleNames = List.copyOf(enabledRuleNames);
    }
}
