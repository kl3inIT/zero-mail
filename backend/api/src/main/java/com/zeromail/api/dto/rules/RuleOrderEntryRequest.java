package com.zeromail.api.dto.rules;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record RuleOrderEntryRequest(
        @NotNull UUID ruleId, @NotNull @PositiveOrZero Integer entityVersion) {}
