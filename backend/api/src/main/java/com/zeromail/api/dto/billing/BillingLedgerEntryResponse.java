package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.projection.BillingLedgerEntrySnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "id",
            "timestamp",
            "type",
            "description",
            "amountCredits",
            "balanceAfterCredits"
        })
public record BillingLedgerEntryResponse(
        UUID id,
        Instant timestamp,
        String type,
        String description,
        int amountCredits,
        int balanceAfterCredits,
        String reference) {

    public static BillingLedgerEntryResponse from(BillingLedgerEntrySnapshot snapshot) {
        return new BillingLedgerEntryResponse(
                snapshot.id(),
                snapshot.timestamp(),
                snapshot.kind().toLowerCase(Locale.ROOT),
                snapshot.description(),
                snapshot.amountCredits(),
                snapshot.balanceAfterCredits(),
                snapshot.reference());
    }
}
