package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantHealthSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"tokenRefreshStatus", "watchStatus", "pubsubBacklogCount"})
public record TenantHealthResponse(
        String tokenRefreshStatus,
        Instant lastTokenRefreshAt,
        String watchStatus,
        Instant lastPubSubPushAt,
        int pubsubBacklogCount) {

    public static TenantHealthResponse from(TenantHealthSnapshot tenantHealthSnapshot) {
        return new TenantHealthResponse(
                tenantHealthSnapshot.tokenRefreshStatus(),
                tenantHealthSnapshot.lastTokenRefreshAt(),
                tenantHealthSnapshot.watchStatus(),
                tenantHealthSnapshot.lastPubSubPushAt(),
                tenantHealthSnapshot.pubsubBacklogCount());
    }
}
