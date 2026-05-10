package com.zeromail.core.billing.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.zeromail.core.billing.domain.CallSite;

/**
 * Watchdog-only projection read outside Hibernate tenant filtering. Callers must bind
 * TenantContext to {@link #tenantId()} before mutating the reservation through JPA.
 */
public record StaleReservation(
        UUID id,
        UUID tenantId,
        OffsetDateTime createdAt,
        int amountCredits,
        CallSite callSite) {
}
