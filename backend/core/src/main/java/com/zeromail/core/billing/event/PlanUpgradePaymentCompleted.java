package com.zeromail.core.billing.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PlanUpgradePaymentCompleted(
        UUID tenantId,
        UUID bankTransferIntentId,
        String bankTransferCode,
        String planCode,
        String provider,
        String providerTransactionId,
        long amountVnd,
        String currency,
        Instant paidAt) {

    public PlanUpgradePaymentCompleted {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(bankTransferIntentId, "bankTransferIntentId must not be null");
        bankTransferCode = requireText(bankTransferCode, "bankTransferCode");
        planCode = requireText(planCode, "planCode");
        provider = requireText(provider, "provider");
        providerTransactionId = requireText(providerTransactionId, "providerTransactionId");
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        currency = requireText(currency, "currency");
        Objects.requireNonNull(paidAt, "paidAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}
