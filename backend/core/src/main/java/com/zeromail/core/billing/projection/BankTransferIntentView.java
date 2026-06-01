package com.zeromail.core.billing.projection;

import com.zeromail.core.billing.persistence.BillingBankTransferIntentEntity;
import java.time.Instant;
import java.util.UUID;

public record BankTransferIntentView(
        UUID id,
        String code,
        String planCode,
        long amountVnd,
        String currency,
        String status,
        Instant expiresAt,
        String bankCode,
        String bankName,
        String accountNumber,
        String accountName,
        String transferContent,
        String qrUrl) {

    public static BankTransferIntentView from(BillingBankTransferIntentEntity intent) {
        return new BankTransferIntentView(
                intent.getId(),
                intent.getCode(),
                intent.getPlanCodeSnapshot(),
                intent.getAmountVnd(),
                intent.getCurrency(),
                intent.getStatus(),
                intent.getExpiresAt(),
                intent.getBankCodeSnapshot(),
                intent.getBankNameSnapshot(),
                intent.getAccountNumberSnapshot(),
                intent.getAccountNameSnapshot(),
                intent.getTransferContentSnapshot(),
                intent.getQrUrlSnapshot());
    }
}
