package com.zeromail.core.referral.projection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReferralDashboardSnapshot(
        ReferralCampaignSnapshot campaign,
        int totalSuccessfulReferrals,
        int activeReferrerTenants,
        ReferralLeaderboardRow currentTopTenant,
        List<ReferralLeaderboardRow> leaderboard,
        List<ReferralTimeSeriesPoint> timeSeries,
        Instant snapshotAt) {

    public ReferralDashboardSnapshot {
        Objects.requireNonNull(campaign, "campaign must not be null");
        leaderboard = List.copyOf(leaderboard);
        timeSeries = List.copyOf(timeSeries);
        Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");
    }
}
