package com.zeromail.api.dto.onboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SelectTemplateRequest(
        @NotBlank
        @Pattern(regexp = "archive-receipts|label-newsletters|pin-calendar")
        String templateKey) {}
