package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantActivityEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String actionLabel,
        String detail,
        String status,
        Integer durationSeconds,
        String source,
        boolean legacyDataMissing) {

    public TenantActivityEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(actionLabel, "actionLabel must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }
}
