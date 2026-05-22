package com.zeromail.core.billing.projection;

import java.time.Instant;

public record BillingCreditSummary(
        int availableCredits,
        int heldCredits,
        int betaCredits,
        int paidCredits,
        int monthlyGrantCredits,
        Instant resetsAt,
        boolean freeDuringBeta) {}
