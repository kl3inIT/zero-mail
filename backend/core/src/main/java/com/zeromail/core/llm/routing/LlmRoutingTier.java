package com.zeromail.core.llm.routing;

import com.zeromail.core.shared.lang.OrderedEnum;
import java.util.NoSuchElementException;

public enum LlmRoutingTier implements OrderedEnum {
    PRIMARY("PRIMARY", 10),
    FALLBACK("FALLBACK", 20),
    LAST_RESORT("LAST_RESORT", 30);

    private final String id;
    private final int weight;

    LlmRoutingTier(String id, int weight) {
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

    public static LlmRoutingTier fromId(String id) {
        for (LlmRoutingTier tier : values()) {
            if (tier.id.equals(id)) return tier;
        }
        throw new NoSuchElementException("Unknown LlmRoutingTier id: " + id);
    }
}
