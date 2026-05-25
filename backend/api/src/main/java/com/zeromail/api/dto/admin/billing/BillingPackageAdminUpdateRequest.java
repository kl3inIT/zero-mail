package com.zeromail.api.dto.admin.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BillingPackageAdminUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @Min(1) long priceVnd,
        @Size(max = 512) String description,
        @NotNull Boolean active,
        @PositiveOrZero int displayOrder) {}
