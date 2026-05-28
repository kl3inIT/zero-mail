package com.zeromail.core.billing.projection;

import java.util.List;

/**
 * Read-side projection of an active billing plan. {@code features} holds the enabled feature
 * catalog entries this plan grants access to, sorted by the catalog's own sort order.
 */
public record BillingPlanView(
        String code,
        String displayName,
        short tierRank,
        String billingCycle,
        String currency,
        long priceVnd,
        int monthlyCreditAllowance,
        int sortOrder,
        List<PlanFeatureSummary> features) {}
