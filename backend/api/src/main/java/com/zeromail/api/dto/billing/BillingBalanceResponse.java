package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.projection.BillingCreditSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
        requiredProperties = {
            "availableCredits",
            "heldCredits",
            "currency",
            "monthlyCredits",
            "additionalCredits",
            "monthlyCreditAllowance",
            "resetsAt"
        })
public record BillingBalanceResponse(
        int availableCredits,
        int heldCredits,
        String currency,
        int monthlyCredits,
        int additionalCredits,
        int monthlyCreditAllowance,
        Instant resetsAt) {

    public static BillingBalanceResponse from(BillingCreditSummary summary) {
        return new BillingBalanceResponse(
                summary.availableCredits(),
                summary.heldCredits(),
                "credits",
                summary.monthlyCredits(),
                summary.additionalCredits(),
                summary.monthlyCreditAllowance(),
                summary.resetsAt());
    }
}
