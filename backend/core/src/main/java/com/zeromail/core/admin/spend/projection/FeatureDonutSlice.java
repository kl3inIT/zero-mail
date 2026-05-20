package com.zeromail.core.admin.spend.projection;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One slice of the feature donut chart on /admin/spend. {@code feature} is the {@code Feature} enum
 * name (CHAT / TRIAGE / DRAFT). {@code percentOfTotal} is in the range [0.0, 100.0].
 */
public record FeatureDonutSlice(
        String feature, BigDecimal totalCost, int callCount, double percentOfTotal) {

    public FeatureDonutSlice {
        Objects.requireNonNull(feature, "feature must not be null");
        Objects.requireNonNull(totalCost, "totalCost must not be null");
    }
}
