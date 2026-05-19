package com.zeromail.api.dto.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = "paused")
public record TriagePauseRequest(@NotNull Boolean paused) {}
