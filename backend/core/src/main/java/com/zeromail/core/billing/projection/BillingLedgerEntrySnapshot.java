package com.zeromail.core.billing.projection;

import java.time.Instant;
import java.util.UUID;

public record BillingLedgerEntrySnapshot(
        UUID id,
        Instant timestamp,
        String kind,
        String description,
        int amountCredits,
        int balanceAfterCredits,
        String reference) {}
