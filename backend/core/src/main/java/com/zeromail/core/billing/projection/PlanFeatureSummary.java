package com.zeromail.core.billing.projection;

/**
 * Read-side projection of one enabled feature available to a billing plan. {@code creditCost}
 * already resolves any {@code plan_feature_permission.credit_cost_override} on top of the {@code
 * feature_catalog.default_credit_cost}. Null {@code daily}/{@code monthlyInvocationLimit} =
 * unlimited.
 */
public record PlanFeatureSummary(
        String code,
        String displayName,
        String description,
        String category,
        int creditCost,
        Integer dailyInvocationLimit,
        Integer monthlyInvocationLimit,
        int sortOrder) {}
