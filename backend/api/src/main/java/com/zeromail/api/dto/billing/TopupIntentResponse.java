package com.zeromail.api.dto.billing;

import java.time.Instant;

public record TopupIntentResponse(
    String code, long amountVnd, Instant expiresAt, String qrPayload) {}
