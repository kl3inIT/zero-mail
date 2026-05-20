package com.zeromail.core.admin.spend.projection;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Aggregate KPI tile values for the /admin/spend dashboard. Today / 7d / 30d windows; each window
 * carries platform, BYOK, and unknown cost buckets separately per R-8F-H1 (row-level credential
 * classification) and R-8F-H9 (UNKNOWN reserved for backfill).
 *
 * <p>All monetary values are USD with 6-decimal precision. {@code unknownCost} is the sum of rows
 * whose {@code credential_source} column is {@code 'UNKNOWN'} — typically historical rows predating
 * the row-level classification rollout. The UI surfaces a caveat caption when {@code unknownCost >
 * 0} in the selected range.
 */
public record SpendKpis(
        BigDecimal todayPlatformCost,
        BigDecimal todayByokCost,
        BigDecimal todayUnknownCost,
        BigDecimal sevenDayPlatformCost,
        BigDecimal sevenDayByokCost,
        BigDecimal sevenDayUnknownCost,
        BigDecimal thirtyDayPlatformCost,
        BigDecimal thirtyDayByokCost,
        BigDecimal thirtyDayUnknownCost,
        int todayCallCount,
        int sevenDayCallCount,
        int thirtyDayCallCount) {

    public SpendKpis {
        Objects.requireNonNull(todayPlatformCost, "todayPlatformCost must not be null");
        Objects.requireNonNull(todayByokCost, "todayByokCost must not be null");
        Objects.requireNonNull(todayUnknownCost, "todayUnknownCost must not be null");
        Objects.requireNonNull(sevenDayPlatformCost, "sevenDayPlatformCost must not be null");
        Objects.requireNonNull(sevenDayByokCost, "sevenDayByokCost must not be null");
        Objects.requireNonNull(sevenDayUnknownCost, "sevenDayUnknownCost must not be null");
        Objects.requireNonNull(thirtyDayPlatformCost, "thirtyDayPlatformCost must not be null");
        Objects.requireNonNull(thirtyDayByokCost, "thirtyDayByokCost must not be null");
        Objects.requireNonNull(thirtyDayUnknownCost, "thirtyDayUnknownCost must not be null");
    }
}
