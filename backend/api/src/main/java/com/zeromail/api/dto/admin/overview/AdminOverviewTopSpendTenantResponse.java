package com.zeromail.api.dto.admin.overview;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.overview.projection.AdminOverviewTopSpendTenant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "tenantId",
            "tenantDisplayName",
            "llmCallCount",
            "chargedCredits",
            "totalCostUsd"
        })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminOverviewTopSpendTenantResponse(
        UUID tenantId,
        String tenantDisplayName,
        String ownerEmail,
        String primaryEmail,
        int llmCallCount,
        long chargedCredits,
        BigDecimal totalCostUsd) {

    public static AdminOverviewTopSpendTenantResponse from(AdminOverviewTopSpendTenant tenant) {
        return new AdminOverviewTopSpendTenantResponse(
                tenant.tenantId(),
                tenant.tenantDisplayName(),
                tenant.ownerEmail(),
                tenant.primaryEmail(),
                tenant.llmCallCount(),
                tenant.chargedCredits(),
                tenant.totalCostUsd());
    }
}
