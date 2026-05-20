package com.zeromail.core.admin.spend.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Raw per-tenant aggregate returned by the repository (pre k-anonymity). The service collapses
 * deleted/orphan tenants into the rollup row via {@link
 * com.zeromail.core.admin.spend.usecases.SpendAggregateQueryService}.
 *
 * <p>{@code tenantId} and {@code gmailAccountEmail} are {@code null} when the underlying row's
 * tenant FK is broken (cascade delete) or its gmail_connection row is missing.
 */
public record TenantSpendBucket(
        UUID tenantId,
        String gmailAccountEmail,
        BigDecimal totalCost,
        BigDecimal unknownCost,
        int callCount) {}
