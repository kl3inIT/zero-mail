package com.zeromail.api.dto.admin.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BillingPackageAdminCreateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^PKG_[A-Z0-9_]{1,60}$") String code,
        @NotBlank @Size(max = 120) String name,
        @Min(1) long priceVnd,
        @Min(1) int creditAmount,
        @Size(max = 512) String description,
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 120) String> includedFeatures,
        Boolean featured,
        Boolean active,
        @PositiveOrZero int displayOrder) {}
