package com.zeromail.api.dto.admin.tenant;

import com.zeromail.api.security.validation.NoSentinelLeak;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"reason"})
public record TenantActionRequest(
        @NotBlank(message = "error.admin.reason_too_short") @Size(min = 8, max = 500, message = "error.admin.reason_too_short") @NoSentinelLeak
                String reason,
        @Email String confirmEmail) {}
