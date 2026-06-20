package com.zeromail.core.referral.projection;

import java.time.Instant;
import java.util.Objects;

public record ReferralTimeSeriesPoint(Instant bucketStart, int successfulReferrals) {

    public ReferralTimeSeriesPoint {
        Objects.requireNonNull(bucketStart, "bucketStart must not be null");
    }
}
