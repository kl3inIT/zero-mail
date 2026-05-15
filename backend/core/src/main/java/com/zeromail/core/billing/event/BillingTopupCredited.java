package com.zeromail.core.billing.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BillingTopupCredited(
        UUID tenantId,
        UUID intentId,
        String orderCode,
        String packageCode,
        String packageName,
        long amountVnd,
        int creditAmount,
        String sepayTransactionId,
        Instant creditedAt) {

    public BillingTopupCredited {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(intentId, "intentId must not be null");
        orderCode = requireText(orderCode, "orderCode");
        packageCode = normalizeNullableText(packageCode);
        packageName = normalizeNullableText(packageName);
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        if (creditAmount <= 0) {
            throw new IllegalArgumentException("creditAmount must be positive");
        }
        sepayTransactionId = requireText(sepayTransactionId, "sepayTransactionId");
        Objects.requireNonNull(creditedAt, "creditedAt must not be null");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isBlank() ? null : trimmedValue;
    }
}
