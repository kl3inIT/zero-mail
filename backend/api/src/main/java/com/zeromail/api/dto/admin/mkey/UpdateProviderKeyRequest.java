package com.zeromail.api.dto.admin.mkey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Patches the operator-facing metadata for a single credential row. Either field may be null or
 * blank — null clears the value on the row.
 */
@Schema(name = "UpdateProviderKeyRequest")
public record UpdateProviderKeyRequest(
        @Size(max = 64) String label, @Size(max = 500) String baseUrl) {}
