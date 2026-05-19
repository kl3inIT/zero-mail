package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.domain.CreditBalance;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"availableCredits", "heldCredits", "currency"})
public record BillingBalanceResponse(int availableCredits, int heldCredits, String currency) {

    public static BillingBalanceResponse from(CreditBalance balance) {
        return new BillingBalanceResponse(
                balance.availableCredits(), balance.heldCredits(), "credits");
    }
}
