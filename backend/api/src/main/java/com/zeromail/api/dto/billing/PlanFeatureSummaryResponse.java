package com.zeromail.api.dto.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.billing.projection.PlanFeatureSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"code", "displayName", "category", "creditCost"})
public record PlanFeatureSummaryResponse(
        String code,
        String displayName,
        String description,
        @Schema(allowableValues = {"TRIAGE", "COMPOSE", "RULES", "CLEANUP", "INTERNAL"})
                String category,
        int creditCost,
        @Schema(description = "Null = unlimited") Integer dailyInvocationLimit,
        @Schema(description = "Null = unlimited") Integer monthlyInvocationLimit) {

    public static PlanFeatureSummaryResponse from(PlanFeatureSummary summary) {
        return new PlanFeatureSummaryResponse(
                summary.code(),
                summary.displayName(),
                summary.description(),
                summary.category(),
                summary.creditCost(),
                summary.dailyInvocationLimit(),
                summary.monthlyInvocationLimit());
    }
}
