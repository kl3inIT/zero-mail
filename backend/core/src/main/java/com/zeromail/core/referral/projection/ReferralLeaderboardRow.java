package com.zeromail.core.referral.projection;

import java.util.Objects;
import java.util.UUID;

public record ReferralLeaderboardRow(
        UUID tenantId, String tenantDisplayName, int successfulReferrals, int rank) {

    public ReferralLeaderboardRow {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(tenantDisplayName, "tenantDisplayName must not be null");
    }
}
