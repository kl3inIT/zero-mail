package com.zeromail.api.dto.billing;

import java.time.Instant;

import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;

public record TopupIntentResponse(
    String code, long amountVnd, Instant expiresAt, String qrPayload) {

  public static TopupIntentResponse from(BillingTopupIntentEntity intent) {
    return new TopupIntentResponse(
        intent.getCode(), intent.getAmountVnd(), intent.getExpiresAt(), null);
  }
}
