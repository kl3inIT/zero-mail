package com.zeromail.api.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(requiredProperties = {"planCode", "paymentMethod"})
public record BillingCheckoutRequest(
        @NotBlank @Schema(
                        description = "Selected billing plan code",
                        allowableValues = {"PLUS", "PRO"})
                String planCode,
        @NotBlank @Schema(
                        description = "Payment method for this checkout",
                        allowableValues = {"LEMON_SQUEEZY", "SEPAY_BANK_TRANSFER"})
                String paymentMethod) {}
