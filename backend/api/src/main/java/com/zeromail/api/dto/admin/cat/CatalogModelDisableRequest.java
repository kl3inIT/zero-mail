package com.zeromail.api.dto.admin.cat;

import com.zeromail.api.security.validation.NoSentinelLeak;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CatalogModelDisableRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._:/\\-]{1,128}$") String modelId,
        @NotBlank(message = "error.admin.reason_too_short") @Size(min = 8, max = 500, message = "error.admin.reason_too_short") @NoSentinelLeak
                String reason,
        boolean confirmedPinned,
        int pinnedCountAcknowledged) {}
