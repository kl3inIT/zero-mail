package com.zeromail.core.admin.spend.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Single row in the stacked-bar chart on /admin/spend. Each row carries the daily aggregate for one
 * (bucketDate, provider) pair, split three ways by credential source per R-8F-H1 + R-8F-H9.
 *
 * <p>UI renders three stacked segments per bar — green = platform, blue = BYOK, gray = unknown.
 */
public record ProviderStackBarRow(
        Instant bucketDate,
        String provider,
        BigDecimal platformCost,
        BigDecimal byokCost,
        BigDecimal unknownCost,
        int callCount) {

    public ProviderStackBarRow {
        Objects.requireNonNull(bucketDate, "bucketDate must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(platformCost, "platformCost must not be null");
        Objects.requireNonNull(byokCost, "byokCost must not be null");
        Objects.requireNonNull(unknownCost, "unknownCost must not be null");
    }
}
