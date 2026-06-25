package com.zeromail.core.admin.billing.projection;

import java.util.UUID;

public record AdminBillingPlanRow(
        UUID planId,
        String code,
        String displayName,
        short tierRank,
        String billingCycle,
        String currency,
        long priceVnd,
        int monthlyCreditAllowance,
        boolean active,
        int sortOrder) {}
