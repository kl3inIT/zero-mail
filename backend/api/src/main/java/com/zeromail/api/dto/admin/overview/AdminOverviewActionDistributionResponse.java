package com.zeromail.api.dto.admin.overview;

import com.zeromail.core.admin.overview.projection.AdminOverviewActionDistribution;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"key", "label", "count"})
public record AdminOverviewActionDistributionResponse(String key, String label, int count) {

    public static AdminOverviewActionDistributionResponse from(
            AdminOverviewActionDistribution actionDistribution) {
        return new AdminOverviewActionDistributionResponse(
                actionDistribution.key(), actionDistribution.label(), actionDistribution.count());
    }
}
