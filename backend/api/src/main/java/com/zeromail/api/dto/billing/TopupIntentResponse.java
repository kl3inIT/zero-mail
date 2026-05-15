package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import java.time.Instant;

public record TopupIntentResponse(
        String orderCode,
        String packageCode,
        String packageName,
        long amountVnd,
        int creditAmount,
        Instant expiresAt,
        String bankCode,
        String bankName,
        String accountNumber,
        String accountName,
        String transferContent,
        String qrPayload) {

    public static TopupIntentResponse from(BillingTopupIntentEntity intent) {
        Integer creditAmountSnapshot = intent.getCreditAmountSnapshot();
        if (creditAmountSnapshot == null) {
            throw new IllegalStateException("Billing top-up intent credit snapshot is required");
        }
        return new TopupIntentResponse(
                intent.getCode(),
                intent.getPackageCodeSnapshot(),
                intent.getPackageNameSnapshot(),
                intent.getAmountVnd(),
                creditAmountSnapshot,
                intent.getExpiresAt(),
                intent.getBankCodeSnapshot(),
                intent.getBankNameSnapshot(),
                intent.getAccountNumberSnapshot(),
                intent.getAccountNameSnapshot(),
                intent.getTransferContentSnapshot(),
                intent.getQrPayloadSnapshot());
    }
}
