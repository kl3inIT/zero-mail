package com.zeromail.api.dto.admin.mkey;

import com.zeromail.api.security.AdminRequestBody;
import com.zeromail.core.admin.mkey.domain.KeyFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@AdminRequestBody
@Schema(
        name = "CreateProviderRequest",
        requiredProperties = {
            "providerId",
            "displayName",
            "compatibleType",
            "defaultBaseUrl",
            "plaintextKey",
            "editSessionToken"
        })
public record CreateProviderRequest(
        @NotBlank @Size(min = 2, max = 32) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_:-]{1,31}") String providerId,
        @NotBlank @Size(max = 120) String displayName,
        @NotNull KeyFormat compatibleType,
        @NotBlank @Size(max = 500) String defaultBaseUrl,
        @NotBlank @Size(min = 10, max = 2048) String plaintextKey,
        @Size(max = 64) String label,
        @NotBlank @Size(max = 128) String editSessionToken) {}
