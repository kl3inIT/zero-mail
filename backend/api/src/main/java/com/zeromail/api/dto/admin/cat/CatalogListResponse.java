package com.zeromail.api.dto.admin.cat;

import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.projection.PerFeatureCatalog;
import com.zeromail.core.admin.mkey.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

@Schema(requiredProperties = {"provider", "features"})
public record CatalogListResponse(String provider, Map<String, FeatureCatalogResponse> features) {

    public CatalogListResponse {
        features = Map.copyOf(features);
    }

    public static CatalogListResponse from(
            LlmProvider provider, Map<Feature, PerFeatureCatalog> catalogByFeature) {
        LinkedHashMap<String, FeatureCatalogResponse> features = new LinkedHashMap<>();
        for (Feature feature : Feature.values()) {
            PerFeatureCatalog perFeatureCatalog = catalogByFeature.get(feature);
            features.put(feature.id(), FeatureCatalogResponse.from(perFeatureCatalog));
        }
        return new CatalogListResponse(provider.id(), features);
    }

    @Schema(requiredProperties = {"feature", "models"})
    public record FeatureCatalogResponse(
            String feature, java.util.List<CatalogModelResponse> models, String defaultModelId) {

        public FeatureCatalogResponse {
            models = java.util.List.copyOf(models);
        }

        static FeatureCatalogResponse from(PerFeatureCatalog perFeatureCatalog) {
            return new FeatureCatalogResponse(
                    perFeatureCatalog.feature().id(),
                    perFeatureCatalog.models().stream().map(CatalogModelResponse::from).toList(),
                    perFeatureCatalog.defaultModelId());
        }
    }
}
