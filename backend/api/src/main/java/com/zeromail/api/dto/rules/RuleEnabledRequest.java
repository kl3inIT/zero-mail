package com.zeromail.api.dto.rules;

import jakarta.validation.constraints.NotNull;

public record RuleEnabledRequest(@NotNull Boolean enabled) {}
