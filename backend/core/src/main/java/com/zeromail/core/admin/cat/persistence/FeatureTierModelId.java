package com.zeromail.core.admin.cat.persistence;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link FeatureTierModelEntity}. Identifies one ordered slot in a tier's
 * model list. Multiple positions per (feature, tier) form the inner-loop failover chain.
 */
public record FeatureTierModelId(Feature feature, RoutingTier tier, short position)
        implements Serializable {

    public FeatureTierModelId {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(tier, "tier");
        if (position < 1) {
            throw new IllegalArgumentException("position must be >= 1, got " + position);
        }
    }
}
