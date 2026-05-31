package com.zeromail.api.dto.admin.cat;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.domain.RoutingTier;
import com.zeromail.core.admin.cat.persistence.FeatureDefaultProviderEntity;
import com.zeromail.core.admin.cat.persistence.FeatureTierModelEntity;
import com.zeromail.core.llm.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side projection for {@code GET /api/admin/catalog/feature-defaults}. Returns the full
 * runtime-feature × 3-tier matrix as a flat list of bindings. Each binding carries an ordered list
 * of model ids — position 1 is tried first, then 2, etc. Empty cells (a feature/tier with no
 * configured row) are omitted; the FE fills the gaps with empty placeholders.
 */
public record FeatureDefaultMatrixResponse(
        @Schema(description = "All configured (feature, tier) bindings.")
                List<FeatureDefaultBinding> bindings) {

    public static FeatureDefaultMatrixResponse from(
            List<FeatureDefaultProviderEntity> anchorRows, List<FeatureTierModelEntity> modelRows) {
        Map<TierKey, List<String>> modelsByTier = new LinkedHashMap<>();
        for (FeatureTierModelEntity row : modelRows) {
            modelsByTier
                    .computeIfAbsent(
                            new TierKey(row.getFeature(), row.getTier()),
                            tierKey -> new ArrayList<>())
                    .add(row.getModelId());
        }
        List<FeatureDefaultBinding> bindings = new ArrayList<>(anchorRows.size());
        for (FeatureDefaultProviderEntity anchor : anchorRows) {
            List<String> modelIds =
                    modelsByTier.getOrDefault(
                            new TierKey(anchor.getFeature(), anchor.getTier()), List.of());
            bindings.add(
                    new FeatureDefaultBinding(
                            anchor.getFeature(),
                            anchor.getTier(),
                            anchor.getProvider(),
                            List.copyOf(modelIds)));
        }
        return new FeatureDefaultMatrixResponse(bindings);
    }

    public record FeatureDefaultBinding(
            @Schema(
                            allowableValues = {
                                "CHAT",
                                "TRIAGE",
                                "DRAFT",
                                "RULE_AUTHORING",
                                "RULE_PREVIEW_SEMANTIC",
                                "TRIAGE_SEMANTIC",
                                "DRIFT_CHECK"
                            })
                    Feature feature,
            @Schema(allowableValues = {"PRIMARY", "FALLBACK", "LAST_RESORT"}) RoutingTier tier,
            LlmProvider provider,
            @Schema(description = "Models in priority order; position 1 is tried first.")
                    List<String> modelIds) {}

    private record TierKey(Feature feature, RoutingTier tier) {}
}
