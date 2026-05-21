package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantSpendSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(
        requiredProperties = {
            "last7dCallCount",
            "last30dCallCount",
            "spendBucket7d",
            "spendBucket30d",
            "perFeatureCallCount"
        })
public record TenantSpendResponse(
        int last7dCallCount,
        int last30dCallCount,
        @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}) String spendBucket7d,
        @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}) String spendBucket30d,
        Map<String, Integer> perFeatureCallCount) {

    public TenantSpendResponse {
        perFeatureCallCount = Map.copyOf(perFeatureCallCount);
    }

    public static TenantSpendResponse from(TenantSpendSnapshot tenantSpendSnapshot) {
        return new TenantSpendResponse(
                tenantSpendSnapshot.last7dCallCount(),
                tenantSpendSnapshot.last30dCallCount(),
                tenantSpendSnapshot.spendBucket7d(),
                tenantSpendSnapshot.spendBucket30d(),
                tenantSpendSnapshot.perFeatureCallCount());
    }
}
