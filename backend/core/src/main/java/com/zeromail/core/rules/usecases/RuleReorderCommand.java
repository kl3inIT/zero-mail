package com.zeromail.core.rules.usecases;

import java.util.List;
import java.util.UUID;

public record RuleReorderCommand(UUID tenantId, List<RuleOrderEntry> orderedEntries) {

    public RuleReorderCommand {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        orderedEntries = List.copyOf(orderedEntries == null ? List.of() : orderedEntries);
        if (orderedEntries.isEmpty()) {
            throw new IllegalArgumentException("orderedEntries must not be empty");
        }
    }
}
