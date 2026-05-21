package com.zeromail.api.dto.admin.cat;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderEntity;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Read-side projection for {@code GET /api/admin/catalog/feature-defaults}. Returns the full
 * 3-feature × 3-tier matrix as a flat list of bindings. Empty cells (a feature/tier with no
 * configured row) are omitted; the FE fills the gaps with empty placeholders.
 */
public record FeatureDefaultMatrixResponse(
        @Schema(description = "All configured (feature, tier) bindings.")
                List<FeatureDefaultBinding> bindings) {

    public static FeatureDefaultMatrixResponse from(List<FeatureDefaultProviderEntity> rows) {
        return new FeatureDefaultMatrixResponse(
                rows.stream().map(FeatureDefaultBinding::from).toList());
    }

    public record FeatureDefaultBinding(
            @Schema(allowableValues = {"CHAT", "TRIAGE", "DRAFT"}) Feature feature,
            @Schema(allowableValues = {"PRIMARY", "FALLBACK", "LAST_RESORT"}) RoutingTier tier,
            LlmProvider provider,
            String modelId) {

        public static FeatureDefaultBinding from(FeatureDefaultProviderEntity entity) {
            return new FeatureDefaultBinding(
                    entity.getFeature(),
                    entity.getTier(),
                    entity.getProvider(),
                    entity.getModelId());
        }
    }
}
