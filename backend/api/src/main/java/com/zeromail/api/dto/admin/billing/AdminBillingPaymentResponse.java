package com.zeromail.api.dto.admin.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.billing.projection.AdminBillingPaymentRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        requiredProperties = {
            "paymentId",
            "tenantId",
            "customerDisplayName",
            "planCode",
            "periodLabel",
            "amountVnd",
            "currency",
            "paymentMethod",
            "transactionCode",
            "status",
            "createdAt"
        })
public record AdminBillingPaymentResponse(
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
        @Schema(allowableValues = {"PAID", "PENDING", "EXPIRED", "VOIDED"}) String status,
        Instant paidAt,
        Instant createdAt) {

    public static AdminBillingPaymentResponse from(AdminBillingPaymentRow payment) {
        return new AdminBillingPaymentResponse(
                payment.paymentId(),
                payment.tenantId(),
                payment.customerDisplayName(),
                payment.customerEmail(),
                payment.planCode(),
                payment.periodLabel(),
                payment.amountVnd(),
                payment.currency(),
                payment.paymentMethod(),
                payment.transactionCode(),
                payment.status(),
                payment.paidAt(),
                payment.createdAt());
    }
}
