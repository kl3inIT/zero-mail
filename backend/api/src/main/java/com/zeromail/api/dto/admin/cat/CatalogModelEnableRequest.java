package com.zeromail.api.dto.admin.cat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CatalogModelEnableRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._:/\\-]{1,128}$") String modelId) {}
