package com.zeromail.core.admin.cat.domain;

import com.zeromail.core.shared.lang.OrderedEnum;
import java.util.NoSuchElementException;

/**
 * Position in the 3-tier feature-default failover chain. The router walks the tiers in ascending
 * {@link #weight()} order ({@link #PRIMARY} → {@link #FALLBACK} → {@link #LAST_RESORT}) and stops
 * on the first ACTIVE/VERIFIED match. Gaps in weight (10/20/30) follow the GSD convention so a
 * future intermediate tier can slot in without renumbering.
 */
public enum RoutingTier implements OrderedEnum {
    PRIMARY("PRIMARY", 10),
    FALLBACK("FALLBACK", 20),
    LAST_RESORT("LAST_RESORT", 30);

    private final String id;
    private final int weight;

    RoutingTier(String id, int weight) {
        this.id = id;
        this.weight = weight;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int weight() {
        return weight;
    }

    public static RoutingTier fromId(String id) {
        for (RoutingTier tier : values()) {
            if (tier.id.equals(id)) return tier;
        }
        throw new NoSuchElementException("Unknown RoutingTier id: " + id);
    }
}
