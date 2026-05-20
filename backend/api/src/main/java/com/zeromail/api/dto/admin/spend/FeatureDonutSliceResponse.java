package com.zeromail.api.dto.admin.spend;

import com.zeromail.core.admin.spend.projection.FeatureDonutSlice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(requiredProperties = {"feature", "totalCost", "callCount", "percentOfTotal"})
public record FeatureDonutSliceResponse(
        String feature, BigDecimal totalCost, int callCount, double percentOfTotal) {

    public static FeatureDonutSliceResponse from(FeatureDonutSlice slice) {
        return new FeatureDonutSliceResponse(
                slice.feature(), slice.totalCost(), slice.callCount(), slice.percentOfTotal());
    }
}
