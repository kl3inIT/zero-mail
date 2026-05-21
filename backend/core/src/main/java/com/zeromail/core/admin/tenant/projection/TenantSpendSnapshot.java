package com.zeromail.core.admin.tenant.projection;

import java.util.Map;
import java.util.Objects;

public record TenantSpendSnapshot(
        int last7dCallCount,
        int last30dCallCount,
        String spendBucket7d,
        String spendBucket30d,
        Map<String, Integer> perFeatureCallCount) {

    public TenantSpendSnapshot {
        Objects.requireNonNull(spendBucket7d, "spendBucket7d must not be null");
        Objects.requireNonNull(spendBucket30d, "spendBucket30d must not be null");
        perFeatureCallCount = Map.copyOf(perFeatureCallCount);
    }
}
