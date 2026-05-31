package com.zeromail.api.dto.byok;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"active"})
public record ByokActivateRequest(@NotNull Boolean active) {}
