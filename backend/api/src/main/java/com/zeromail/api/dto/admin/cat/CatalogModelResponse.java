package com.zeromail.api.dto.admin.cat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.cat.projection.CatalogModelRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        requiredProperties = {
            "provider",
            "modelId",
            "displayName",
            "defaultModel",
            "recommended",
            "pinnedTenantCount"
        })
public record CatalogModelResponse(
        String provider,
        String modelId,
        String displayName,
        boolean defaultModel,
        boolean recommended,
        BigDecimal costPer1kInput,
        BigDecimal costPer1kOutput,
        Instant deprecatedAt,
        long pinnedTenantCount) {

    public static CatalogModelResponse from(CatalogModelRow catalogModelRow) {
        return new CatalogModelResponse(
                catalogModelRow.provider(),
                catalogModelRow.modelId(),
                catalogModelRow.displayName(),
                catalogModelRow.defaultModel(),
                catalogModelRow.recommended(),
                catalogModelRow.costPer1kInput(),
                catalogModelRow.costPer1kOutput(),
                catalogModelRow.deprecatedAt(),
                catalogModelRow.pinnedTenantCount());
    }
}
