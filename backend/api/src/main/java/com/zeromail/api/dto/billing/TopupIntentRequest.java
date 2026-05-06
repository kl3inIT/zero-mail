package com.zeromail.api.dto.billing;

import jakarta.validation.constraints.Min;

public record TopupIntentRequest(@Min(1) long amountVnd) {}
