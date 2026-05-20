package com.zeromail.core.admin.tenant.projection;

import java.time.Instant;
import java.util.Objects;

public record TenantBillingSnapshot(int creditsBalance, String plan, Instant lastTopUpAt) {

    public TenantBillingSnapshot {
        Objects.requireNonNull(plan, "plan must not be null");
    }
}
