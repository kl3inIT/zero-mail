package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.Objects;

public record TenantHealthSnapshot(
        String tokenRefreshStatus,
        Instant lastTokenRefreshAt,
        String watchStatus,
        Instant lastPubSubPushAt,
        int pubsubBacklogCount) {

    public TenantHealthSnapshot {
        Objects.requireNonNull(tokenRefreshStatus, "tokenRefreshStatus must not be null");
        Objects.requireNonNull(watchStatus, "watchStatus must not be null");
    }
}
