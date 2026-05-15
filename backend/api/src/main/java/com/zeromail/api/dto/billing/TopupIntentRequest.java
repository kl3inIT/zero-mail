package com.zeromail.api.dto.billing;

import jakarta.validation.constraints.NotBlank;

public record TopupIntentRequest(@NotBlank String packageCode) {}
