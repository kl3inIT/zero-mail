package com.zeromail.api.dto.admin.cat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CatalogModelCreateRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._:/\\-]{1,128}$") String modelId,
        @NotBlank @Size(max = 200) String displayName,
        @DecimalMin("0") BigDecimal costPer1kInput,
        @DecimalMin("0") BigDecimal costPer1kOutput,
        boolean recommended) {}
