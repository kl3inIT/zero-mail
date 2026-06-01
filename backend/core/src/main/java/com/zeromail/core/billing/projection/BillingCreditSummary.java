package com.zeromail.core.billing.projection;

import java.time.Instant;

public record BillingCreditSummary(
        int availableCredits,
        int heldCredits,
        int monthlyCredits,
        int additionalCredits,
        int monthlyCreditAllowance,
        Instant resetsAt) {}
