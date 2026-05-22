package com.zeromail.api.dto.admin.mkey;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Atomic reorder of priorities within a provider. The submitted ordering must include every
 * existing key (no additions, no removals); the service assigns sequential priorities 1..N.
 */
@Schema(
        name = "ReorderProviderKeysRequest",
        requiredProperties = {"orderedKeyIds"})
public record ReorderProviderKeysRequest(
        @NotNull @Size(min = 1, max = 16) List<UUID> orderedKeyIds) {}
