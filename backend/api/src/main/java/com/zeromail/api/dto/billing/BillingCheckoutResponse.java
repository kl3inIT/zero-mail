package com.zeromail.api.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"checkoutUrl"})
public record BillingCheckoutResponse(
        @Schema(description = "Hosted Lemon Squeezy checkout URL for the selected billing plan")
                String checkoutUrl) {}
