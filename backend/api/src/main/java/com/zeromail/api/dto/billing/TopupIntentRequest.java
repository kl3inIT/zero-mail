package com.zeromail.api.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(requiredProperties = "packageCode")
public record TopupIntentRequest(@NotBlank String packageCode) {}
