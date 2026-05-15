package com.zeromail.api.dto.billing;

public record SepayWebhookPayload(
        long id,
        String gateway,
        String transactionDate,
        String accountNumber,
        String code,
        String content,
        String transferType,
        long transferAmount,
        long accumulated,
        String subAccount,
        String referenceCode,
        String description,
        String packageCode) {

    public SepayWebhookPayload(
            long id,
            String gateway,
            String transactionDate,
            String accountNumber,
            String code,
            String content,
            String transferType,
            long transferAmount,
            long accumulated,
            String subAccount,
            String referenceCode,
            String description) {
        this(
                id,
                gateway,
                transactionDate,
                accountNumber,
                code,
                content,
                transferType,
                transferAmount,
                accumulated,
                subAccount,
                referenceCode,
                description,
                null);
    }
}
