package com.zeromail.api.dto.admin.grants;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(requiredProperties = {"email"})
public record GrantAdminRequest(
        @NotBlank @Email @Size(max = 320) @Schema(format = "email") String email) {}
