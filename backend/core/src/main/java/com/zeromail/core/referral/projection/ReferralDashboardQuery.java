package com.zeromail.core.referral.projection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReferralDashboardQuery(UUID campaignId, Instant from, Instant to) {

    public ReferralDashboardQuery {
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
    }
}
