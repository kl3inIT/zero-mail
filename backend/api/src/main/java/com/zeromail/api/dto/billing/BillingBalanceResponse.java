package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.projection.BillingCreditSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
        requiredProperties = {
            "availableCredits",
            "heldCredits",
            "currency",
            "betaCredits",
            "paidCredits",
            "monthlyGrantCredits",
            "resetsAt",
            "freeDuringBeta"
        })
public record BillingBalanceResponse(
        int availableCredits,
        int heldCredits,
        String currency,
        int betaCredits,
        int paidCredits,
        int monthlyGrantCredits,
        Instant resetsAt,
        boolean freeDuringBeta) {

    public static BillingBalanceResponse from(BillingCreditSummary summary) {
        return new BillingBalanceResponse(
                summary.availableCredits(),
                summary.heldCredits(),
                "credits",
                summary.betaCredits(),
                summary.paidCredits(),
                summary.monthlyGrantCredits(),
                summary.resetsAt(),
                summary.freeDuringBeta());
    }
}
