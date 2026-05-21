package com.zeromail.api.dto.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.billing.projection.BillingLedgerEntrySnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"entries"})
public record BillingLedgerHistoryResponse(
        List<BillingLedgerEntryResponse> entries, String nextCursor) {

    public static BillingLedgerHistoryResponse from(List<BillingLedgerEntrySnapshot> entries) {
        return new BillingLedgerHistoryResponse(
                entries.stream().map(BillingLedgerEntryResponse::from).toList(), null);
    }
}
