package com.zeromail.core.rules.application;

import java.util.UUID;

public record RuleOrderEntry(UUID ruleId, Integer entityVersion) {

    public RuleOrderEntry {
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId must not be null");
        }
        if (entityVersion == null || entityVersion < 0) {
            throw new IllegalArgumentException("entityVersion must be non-negative");
        }
    }
}
