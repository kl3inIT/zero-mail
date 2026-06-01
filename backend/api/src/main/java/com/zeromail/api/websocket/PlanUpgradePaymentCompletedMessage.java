package com.zeromail.api.websocket;

import com.zeromail.core.billing.event.PlanUpgradePaymentCompleted;
import java.time.Instant;
import java.util.UUID;

public record PlanUpgradePaymentCompletedMessage(
        String type,
        UUID bankTransferIntentId,
        String bankTransferCode,
        String planCode,
        String provider,
        long amountVnd,
        String currency,
        Instant paidAt) {

    private static final String TYPE = "PLAN_UPGRADE_PAYMENT_COMPLETED";

    static PlanUpgradePaymentCompletedMessage from(PlanUpgradePaymentCompleted event) {
        return new PlanUpgradePaymentCompletedMessage(
                TYPE,
                event.bankTransferIntentId(),
                event.bankTransferCode(),
                event.planCode(),
                event.provider(),
                event.amountVnd(),
                event.currency(),
                event.paidAt());
    }
}
