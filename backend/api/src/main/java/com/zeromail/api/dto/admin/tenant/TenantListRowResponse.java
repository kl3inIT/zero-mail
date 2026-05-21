package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantListRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"tenantId", "createdAt", "status", "spendBucket7d"})
public record TenantListRowResponse(
        UUID tenantId,
        Instant createdAt,
        String gmailAccountEmail,
        @Schema(allowableValues = {"ACTIVE", "PAUSED", "DISCONNECTED"}) String status,
        @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}) String spendBucket7d) {

    public static TenantListRowResponse from(TenantListRow tenantListRow) {
        return new TenantListRowResponse(
                tenantListRow.tenantId(),
                tenantListRow.createdAt(),
                tenantListRow.gmailAccountEmail(),
                tenantListRow.status(),
                tenantListRow.spendBucket7d());
    }
}
