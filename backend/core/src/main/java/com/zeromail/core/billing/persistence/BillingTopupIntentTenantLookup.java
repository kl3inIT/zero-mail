package com.zeromail.core.billing.persistence;

import com.zeromail.core.billing.domain.BillingTopupIntentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Webhook-only projection that carries tenant id before the request thread has a bound
 * TenantContext.
 */
public record BillingTopupIntentTenantLookup(
        UUID id,
        UUID tenantId,
        String code,
        long amountVnd,
        String packageCodeSnapshot,
        Integer creditAmountSnapshot,
        BillingTopupIntentStatus status,
        OffsetDateTime expiresAt) {}
