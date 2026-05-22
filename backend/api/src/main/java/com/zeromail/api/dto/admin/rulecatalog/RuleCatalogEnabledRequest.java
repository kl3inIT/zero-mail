package com.zeromail.api.dto.admin.rulecatalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(requiredProperties = {"enabled", "reason"})
public record RuleCatalogEnabledRequest(boolean enabled, @NotBlank String reason) {}
