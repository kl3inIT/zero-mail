package com.zeromail.core.referral.projection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ReferralTenantRankingSnapshot(
        List<ReferralLeaderboardRow> leaderboard,
        ReferralLeaderboardRow currentTenant,
        int totalRankedTenants,
        Instant snapshotAt) {

    public ReferralTenantRankingSnapshot {
        Objects.requireNonNull(leaderboard, "leaderboard must not be null");
        Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");
        leaderboard = List.copyOf(leaderboard);
        if (totalRankedTenants < 0) {
            throw new IllegalArgumentException("totalRankedTenants must not be negative");
        }
    }
}
