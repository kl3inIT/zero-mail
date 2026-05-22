package com.zeromail.core.rules.catalog.usecases;

import java.util.Objects;
import java.util.UUID;

public record RuleCatalogOrderEntry(UUID itemId, int displayOrder) {

    public RuleCatalogOrderEntry {
        Objects.requireNonNull(itemId, "itemId must not be null");
        RuleCatalogCommandText.requireNonNegativeDisplayOrder(displayOrder, "displayOrder");
    }
}
