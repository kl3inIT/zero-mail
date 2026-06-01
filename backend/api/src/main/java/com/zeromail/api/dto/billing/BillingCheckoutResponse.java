package com.zeromail.api.dto.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.billing.projection.PlanUpgradeCheckoutView;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"paymentMethod", "status"})
public record BillingCheckoutResponse(
        @Schema(
                        description = "Payment method used for this checkout",
                        allowableValues = {"LEMON_SQUEEZY", "SEPAY_BANK_TRANSFER"})
                String paymentMethod,
        @Schema(
                        description = "Client action state",
                        allowableValues = {"REDIRECT_REQUIRED", "WAITING_FOR_TRANSFER"})
                String status,
        @Schema(description = "Hosted Lemon Squeezy checkout URL when redirect is required")
                String checkoutUrl,
        @Schema(
                        description =
                                "Bank transfer instructions when paymentMethod is SEPAY_BANK_TRANSFER")
                BankTransferIntentResponse bankTransferIntent) {

    public static BillingCheckoutResponse from(PlanUpgradeCheckoutView checkout) {
        return new BillingCheckoutResponse(
                checkout.paymentMethod(),
                checkout.status(),
                checkout.checkoutUrl(),
                checkout.bankTransferIntent() == null
                        ? null
                        : BankTransferIntentResponse.from(checkout.bankTransferIntent()));
    }
}
