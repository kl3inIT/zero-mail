package com.zeromail.api.dto.tenant;

import jakarta.validation.constraints.NotNull;

public record TriagePauseRequest(@NotNull Boolean paused) {}
