package com.zeromail.core.billing.projection;

import java.util.List;

/**
 * Read-side projection of the active billing-plan catalog for a specific tenant. Wraps the plan
 * list with the tenant's currently-active plan code so the UI can flag the matching card without a
 * second roundtrip.
 *
 * @param currentPlanCode the tenant's active plan code (e.g. "FREE", "PLUS"); never null — falls
 *     back to "FREE" when the tenant has no {@code subscription} row
 * @param plans every active plan in the catalog, sorted by {@code tierRank}
 */
public record BillingPlanCatalogView(String currentPlanCode, List<BillingPlanView> plans) {}
