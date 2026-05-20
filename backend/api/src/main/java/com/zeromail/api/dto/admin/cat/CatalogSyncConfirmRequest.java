package com.zeromail.api.dto.admin.cat;

import com.zeromail.api.security.validation.NoSentinelLeak;
import jakarta.validation.constraints.Size;

public record CatalogSyncConfirmRequest(
        @Size(max = 500, message = "error.admin.reason_too_short") @NoSentinelLeak String reason) {}
