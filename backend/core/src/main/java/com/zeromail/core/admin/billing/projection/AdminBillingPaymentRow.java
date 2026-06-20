package com.zeromail.core.admin.billing.projection;

import java.time.Instant;
import java.util.UUID;

public record AdminBillingPaymentRow(
        String paymentId,
        UUID tenantId,
        String customerDisplayName,
        String customerEmail,
        String planCode,
        String periodLabel,
        long amountVnd,
        String currency,
        String paymentMethod,
        String transactionCode,
        String status,
        Instant paidAt,
        Instant createdAt) {}
