package com.zeromail.api.dto.billing;

import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import java.time.Instant;

public record TopupIntentResponse(
        String code, long amountVnd, Instant expiresAt, String qrPayload) {

    public static TopupIntentResponse from(BillingTopupIntentEntity intent) {
        return new TopupIntentResponse(
                intent.getCode(), intent.getAmountVnd(), intent.getExpiresAt(), null);
    }
}
