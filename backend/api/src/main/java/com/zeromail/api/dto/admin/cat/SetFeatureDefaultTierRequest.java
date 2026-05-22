package com.zeromail.api.dto.admin.cat;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body for {@code PUT /api/admin/catalog/feature-defaults}. Assigns one (feature, tier) cell of the
 * routing matrix to a provider plus an ordered list of models — position 0 of {@code modelIds} is
 * tried first, then 1, etc.
 */
public record SetFeatureDefaultTierRequest(
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"CHAT", "TRIAGE", "DRAFT"})
                @NotNull Feature feature,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"PRIMARY", "FALLBACK", "LAST_RESORT"})
                @NotNull RoutingTier tier,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LlmProvider provider,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description =
                                "Models in priority order. Position 0 is tried first, then 1, etc. Each id must reference a VERIFIED or STALE row for the chosen provider.")
                @NotNull @Size(min = 1, max = 16) List<@Valid @NotBlank String> modelIds) {}
