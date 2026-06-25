package com.zeromail.core.referral.projection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReferralTenantCode(
        UUID referralCodeId,
        UUID campaignId,
        UUID ownerTenantId,
        String code,
        String status,
        Instant createdAt) {

    public ReferralTenantCode {
        Objects.requireNonNull(referralCodeId, "referralCodeId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        Objects.requireNonNull(ownerTenantId, "ownerTenantId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
