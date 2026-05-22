package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantDetailOverview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"tenantId", "createdAt", "status", "rulesCount"})
public record TenantDetailResponse(
        UUID tenantId,
        Instant createdAt,
        String gmailAccountEmail,
        @Schema(allowableValues = {"ACTIVE", "PAUSED", "DISCONNECTED"}) String status,
        Instant lastActivityAt,
        int rulesCount) {

    public static TenantDetailResponse from(TenantDetailOverview tenantDetailOverview) {
        return new TenantDetailResponse(
                tenantDetailOverview.tenantId(),
                tenantDetailOverview.createdAt(),
                tenantDetailOverview.gmailAccountEmail(),
                tenantDetailOverview.status(),
                tenantDetailOverview.lastActivityAt(),
                tenantDetailOverview.rulesCount());
    }
}
