package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantBillingSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"creditsBalance", "plan"})
public record TenantBillingResponse(int creditsBalance, String plan, Instant lastTopUpAt) {

    public static TenantBillingResponse from(TenantBillingSnapshot tenantBillingSnapshot) {
        return new TenantBillingResponse(
                tenantBillingSnapshot.creditsBalance(),
                tenantBillingSnapshot.plan(),
                tenantBillingSnapshot.lastTopUpAt());
    }
}
