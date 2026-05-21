package com.zeromail.api.dto.admin.mkey;

import com.zeromail.core.admin.mkey.domain.KeyFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Adds a new credential row to a provider's failover chain. The key is probed before being
 * persisted; non-OK results are rejected.
 */
@Schema(
        name = "AddProviderKeyRequest",
        requiredProperties = {"plaintextKey", "keyFormat", "editSessionToken"})
public record AddProviderKeyRequest(
        @NotBlank @Size(max = 2048) String plaintextKey,
        @NotNull KeyFormat keyFormat,
        @Size(max = 500) String baseUrl,
        @Size(max = 64) String label,
        @NotBlank @Size(max = 128) String editSessionToken) {}
