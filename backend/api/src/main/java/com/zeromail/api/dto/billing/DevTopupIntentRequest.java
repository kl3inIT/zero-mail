package com.zeromail.api.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DevTopupIntentRequest(@NotNull UUID tenantId, @NotBlank String packageCode) {}
