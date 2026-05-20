package com.zeromail.core.admin.spend.projection;

import java.math.BigDecimal;

/**
 * Raw {@code GROUP BY feature} aggregate returned by the repository. The service derives {@link
 * FeatureDonutSlice} from a list of these by computing {@code percentOfTotal}.
 */
public record FeatureSpendBucket(String feature, BigDecimal totalCost, int callCount) {}
