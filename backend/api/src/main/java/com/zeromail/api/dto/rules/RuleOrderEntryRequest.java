package com.zeromail.api.dto.rules;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RuleOrderEntryRequest(
    @NotNull UUID ruleId, @NotNull @PositiveOrZero Integer entityVersion) {}
