package com.zeromail.api.dto.admin.grants;

import com.zeromail.api.security.validation.NoSentinelLeak;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"reason"})
public record RevokeAdminRequest(
        @NotBlank @Size(min = 8, max = 500) @NoSentinelLeak String reason) {}
