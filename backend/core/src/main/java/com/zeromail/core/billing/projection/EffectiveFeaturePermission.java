package com.zeromail.core.billing.projection;

import com.zeromail.core.billing.domain.CallSite;

/**
 * Resolved per-tenant feature permission, ready for the credit ledger to act on.
 *
 * @param callSite the requested call site
 * @param planCode the billing plan code the tenant is on at lookup time (FREE / PLUS / PRO)
 * @param effectiveCreditCost the per-call cost after applying {@code plan_feature_permission
 *     .credit_cost_override}; falls back to {@code feature_catalog.default_credit_cost} when no
 *     override is set
 * @param dailyInvocationLimit null = unlimited (no daily cap)
 * @param monthlyInvocationLimit null = unlimited (no monthly cap)
 */
public record EffectiveFeaturePermission(
        CallSite callSite,
        String planCode,
        int effectiveCreditCost,
        Integer dailyInvocationLimit,
        Integer monthlyInvocationLimit) {}
