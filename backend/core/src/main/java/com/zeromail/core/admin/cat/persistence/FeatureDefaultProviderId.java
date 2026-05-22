package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link FeatureDefaultProviderEntity}. Each feature owns three rows —
 * one per {@link RoutingTier} — forming the failover chain.
 */
public record FeatureDefaultProviderId(Feature feature, RoutingTier tier) implements Serializable {

    public FeatureDefaultProviderId {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(tier, "tier");
    }
}
