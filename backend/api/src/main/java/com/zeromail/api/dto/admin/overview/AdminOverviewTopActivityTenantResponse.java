package com.zeromail.api.dto.admin.overview;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.overview.projection.AdminOverviewTopActivityTenant;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "tenantId",
            "tenantDisplayName",
            "observedEmailCount",
            "triageActionCount",
            "failedTriageActionCount",
            "outboundActionCount",
            "blockedOutboundActionCount",
            "failureRatePercent"
        })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminOverviewTopActivityTenantResponse(
        UUID tenantId,
        String tenantDisplayName,
        String ownerEmail,
        String primaryEmail,
        int observedEmailCount,
        int triageActionCount,
        int failedTriageActionCount,
        int outboundActionCount,
        int blockedOutboundActionCount,
        double failureRatePercent) {

    public static AdminOverviewTopActivityTenantResponse from(
            AdminOverviewTopActivityTenant tenant) {
        return new AdminOverviewTopActivityTenantResponse(
                tenant.tenantId(),
                tenant.tenantDisplayName(),
                tenant.ownerEmail(),
                tenant.primaryEmail(),
                tenant.observedEmailCount(),
                tenant.triageActionCount(),
                tenant.failedTriageActionCount(),
                tenant.outboundActionCount(),
                tenant.blockedOutboundActionCount(),
                tenant.failureRatePercent());
    }
}
