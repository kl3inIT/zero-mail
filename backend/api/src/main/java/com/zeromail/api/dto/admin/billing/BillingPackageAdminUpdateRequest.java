package com.zeromail.api.dto.admin.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BillingPackageAdminUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @Min(1) long priceVnd,
        @Min(1) int creditAmount,
        @Size(max = 512) String description,
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 120) String> includedFeatures,
        @NotNull Boolean featured,
        @NotNull Boolean active,
        @PositiveOrZero int displayOrder) {}
