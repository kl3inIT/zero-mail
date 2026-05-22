package com.zeromail.api.dto.admin.mkey;

import com.zeromail.api.security.AdminRequestBody;
import com.zeromail.core.admin.mkey.domain.KeyFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@AdminRequestBody
@Schema(requiredProperties = {"plaintextKey", "keyFormat", "editSessionToken"})
public record TestConnectionRequest(
        @NotBlank @Size(min = 10, max = 500) String plaintextKey,
        @NotNull KeyFormat keyFormat,
        @Size(max = 500) String baseUrl,
        @NotBlank String editSessionToken) {}
