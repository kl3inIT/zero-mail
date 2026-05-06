package com.zeromail.core.billing.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;

/**
 * Webhook-only projection that carries tenant id before the request thread has a bound
 * TenantContext.
 */
public record BillingTopupIntentTenantLookup(
        UUID id,
        UUID tenantId,
        String code,
        long amountVnd,
        BillingTopupIntentStatus status,
        OffsetDateTime expiresAt) {
}
