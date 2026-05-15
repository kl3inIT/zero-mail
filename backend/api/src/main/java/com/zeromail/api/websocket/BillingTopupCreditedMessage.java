package com.zeromail.api.websocket;

import com.zeromail.core.billing.event.BillingTopupCredited;
import java.time.Instant;

public record BillingTopupCreditedMessage(
        String type,
        String orderCode,
        String packageCode,
        String packageName,
        long amountVnd,
        int creditAmount,
        Instant creditedAt) {

    private static final String TYPE = "TOPUP_CREDITED";

    static BillingTopupCreditedMessage from(BillingTopupCredited event) {
        return new BillingTopupCreditedMessage(
                TYPE,
                event.orderCode(),
                event.packageCode(),
                event.packageName(),
                event.amountVnd(),
                event.creditAmount(),
                event.creditedAt());
    }
}
