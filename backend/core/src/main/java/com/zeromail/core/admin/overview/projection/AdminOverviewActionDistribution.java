package com.zeromail.core.admin.overview.projection;

import java.util.Objects;

public record AdminOverviewActionDistribution(String key, String label, int count) {

    public AdminOverviewActionDistribution {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(label, "label must not be null");
    }
}
