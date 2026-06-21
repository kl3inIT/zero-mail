package com.zeromail.api.dto.referral;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.referral.projection.ReferralTenantRankingSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"active"})
public record ReferralMeResponse(
        boolean active,
        UUID campaignId,
        String campaignName,
        String campaignDescription,
        Instant campaignStartsAt,
        Instant campaignEndsAt,
        Boolean webBannerEnabled,
        Boolean countdownEnabled,
        Boolean leaderboardEnabled,
        Integer rewardRankCutoff,
        String rewardNotificationText,
        Boolean bannerImageAvailable,
        String code,
        String url,
        Integer successfulReferrals,
        Integer totalRankedTenants,
        ReferralLeaderboardRowResponse currentTenant,
        List<ReferralLeaderboardRowResponse> leaderboard,
        Instant snapshotAt) {

    public ReferralMeResponse {
        leaderboard = leaderboard == null ? null : List.copyOf(leaderboard);
    }

    public static ReferralMeResponse inactive() {
        return new ReferralMeResponse(
                false, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static ReferralMeResponse active(
            UUID campaignId,
            String campaignName,
            String campaignDescription,
            Instant campaignStartsAt,
            Instant campaignEndsAt,
            boolean webBannerEnabled,
            boolean countdownEnabled,
            boolean leaderboardEnabled,
            int rewardRankCutoff,
            String rewardNotificationText,
            boolean bannerImageAvailable,
            String code,
            String url,
            int successfulReferrals,
            ReferralTenantRankingSnapshot rankingSnapshot,
            UUID ownerTenantId) {
        return new ReferralMeResponse(
                true,
                campaignId,
                campaignName,
                campaignDescription,
                campaignStartsAt,
                campaignEndsAt,
                webBannerEnabled,
                countdownEnabled,
                leaderboardEnabled,
                rewardRankCutoff,
                rewardNotificationText,
                bannerImageAvailable,
                code,
                url,
                successfulReferrals,
                rankingSnapshot.totalRankedTenants(),
                rankingSnapshot.currentTenant() == null
                        ? null
                        : ReferralLeaderboardRowResponse.from(
                                rankingSnapshot.currentTenant(), ownerTenantId),
                rankingSnapshot.leaderboard().stream()
                        .map(
                                referralLeaderboardRow ->
                                        ReferralLeaderboardRowResponse.from(
                                                referralLeaderboardRow, ownerTenantId))
                        .toList(),
                rankingSnapshot.snapshotAt());
    }
}
