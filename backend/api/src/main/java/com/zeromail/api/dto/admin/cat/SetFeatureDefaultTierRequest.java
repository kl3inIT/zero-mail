package com.zeromail.api.dto.admin.cat;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body for {@code PUT /api/admin/catalog/feature-defaults}. Assigns a single (feature, tier) cell
 * of the routing matrix. The 3-tier picker on the FE sends one of these per click.
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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String modelId) {}
