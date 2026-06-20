package com.zeromail.api.dto.admin.referral;

import com.zeromail.core.referral.projection.ReferralDashboardSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
        requiredProperties = {
            "campaign",
            "totalSuccessfulReferrals",
            "activeReferrerTenants",
            "leaderboard",
            "timeSeries",
            "snapshotAt"
        })
public record AdminReferralDashboardResponse(
        AdminReferralCampaignResponse campaign,
        int totalSuccessfulReferrals,
        int activeReferrerTenants,
        AdminReferralLeaderboardRowResponse currentTopTenant,
        List<AdminReferralLeaderboardRowResponse> leaderboard,
        List<AdminReferralTimeSeriesPointResponse> timeSeries,
        Instant snapshotAt) {

    public AdminReferralDashboardResponse {
        leaderboard = List.copyOf(leaderboard);
        timeSeries = List.copyOf(timeSeries);
    }

    public static AdminReferralDashboardResponse from(ReferralDashboardSnapshot snapshot) {
        return new AdminReferralDashboardResponse(
                AdminReferralCampaignResponse.from(snapshot.campaign()),
                snapshot.totalSuccessfulReferrals(),
                snapshot.activeReferrerTenants(),
                snapshot.currentTopTenant() == null
                        ? null
                        : AdminReferralLeaderboardRowResponse.from(snapshot.currentTopTenant()),
                snapshot.leaderboard().stream()
                        .map(AdminReferralLeaderboardRowResponse::from)
                        .toList(),
                snapshot.timeSeries().stream()
                        .map(AdminReferralTimeSeriesPointResponse::from)
                        .toList(),
                snapshot.snapshotAt());
    }
}
