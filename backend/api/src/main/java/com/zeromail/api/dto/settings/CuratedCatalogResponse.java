package com.zeromail.api.dto.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.cat.domain.Feature;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import com.zeromail.core.admin.cat.projection.PerFeatureCatalog;
import com.zeromail.core.admin.cat.usecases.CuratedCatalogQueryService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(requiredProperties = {"features"})
public record CuratedCatalogResponse(Map<String, FeatureCatalogResponse> features) {

    public CuratedCatalogResponse {
        features = Map.copyOf(features);
    }

    public static CuratedCatalogResponse from(
            CuratedCatalogQueryService.CuratedCatalogResult curatedCatalogResult) {
        LinkedHashMap<String, FeatureCatalogResponse> features = new LinkedHashMap<>();
        for (Feature feature : Feature.values()) {
            PerFeatureCatalog perFeatureCatalog = curatedCatalogResult.catalog().get(feature);
            features.put(feature.id(), FeatureCatalogResponse.from(perFeatureCatalog));
        }
        return new CuratedCatalogResponse(features);
    }

    @Schema(requiredProperties = {"feature", "models"})
    public record FeatureCatalogResponse(
            String feature, List<CuratedCatalogModelResponse> models, String defaultModelId) {

        public FeatureCatalogResponse {
            models = List.copyOf(models);
        }

        static FeatureCatalogResponse from(PerFeatureCatalog perFeatureCatalog) {
            return new FeatureCatalogResponse(
                    perFeatureCatalog.feature().id(),
                    perFeatureCatalog.models().stream()
                            .map(CuratedCatalogModelResponse::from)
                            .toList(),
                    perFeatureCatalog.defaultModelId());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            requiredProperties = {
                "provider",
                "modelId",
                "displayName",
                "defaultModel",
                "recommended"
            })
    public record CuratedCatalogModelResponse(
            String provider,
            String modelId,
            String displayName,
            boolean defaultModel,
            boolean recommended,
            BigDecimal costPer1kInput,
            BigDecimal costPer1kOutput,
            Instant deprecatedAt) {

        static CuratedCatalogModelResponse from(CatalogModelRow catalogModelRow) {
            return new CuratedCatalogModelResponse(
                    catalogModelRow.provider(),
                    catalogModelRow.modelId(),
                    catalogModelRow.displayName(),
                    catalogModelRow.defaultModel(),
                    catalogModelRow.recommended(),
                    catalogModelRow.costPer1kInput(),
                    catalogModelRow.costPer1kOutput(),
                    catalogModelRow.deprecatedAt());
        }
    }
}
