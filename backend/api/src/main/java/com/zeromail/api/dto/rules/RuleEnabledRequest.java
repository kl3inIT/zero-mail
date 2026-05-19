package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = "enabled")
public record RuleEnabledRequest(@NotNull Boolean enabled) {}
