package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.projection.BankTransferIntentView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "id",
            "code",
            "planCode",
            "amountVnd",
            "currency",
            "status",
            "expiresAt",
            "bankCode",
            "accountNumber",
            "accountName",
            "transferContent",
            "qrUrl"
        })
public record BankTransferIntentResponse(
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

    public static BankTransferIntentResponse from(BankTransferIntentView intent) {
        return new BankTransferIntentResponse(
                intent.id(),
                intent.code(),
                intent.planCode(),
                intent.amountVnd(),
                intent.currency(),
                intent.status(),
                intent.expiresAt(),
                intent.bankCode(),
                intent.bankName(),
                intent.accountNumber(),
                intent.accountName(),
                intent.transferContent(),
                intent.qrUrl());
    }
}
