package com.zeromail.api.dto.admin.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BillingPackageAdminCreateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 120) String name,
        @Min(1) long priceVnd,
        @Size(max = 512) String description,
        Boolean active,
        @PositiveOrZero int displayOrder) {}
