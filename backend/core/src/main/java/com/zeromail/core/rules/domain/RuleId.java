package com.zeromail.core.rules.domain;

import java.util.Objects;
import java.util.UUID;

public record RuleId(UUID value) {

    public RuleId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
