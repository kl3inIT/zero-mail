package com.zeromail.api.dto.triage;

import jakarta.validation.constraints.NotNull;

public record TriageShadowModeRequest(@NotNull Boolean enabled) {}
