package com.zeromail.api.dto.onboarding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(requiredProperties = "templateKey")
public record SelectTemplateRequest(
        @NotBlank @Pattern(regexp = "archive-receipts|label-newsletters|pin-calendar") @Schema(allowableValues = {"archive-receipts", "label-newsletters", "pin-calendar"})
                String templateKey) {}
