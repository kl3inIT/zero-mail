package com.zeromail.core.referral.usecases;

import com.zeromail.core.referral.persistence.lowlevel.ReferralDashboardReadRepository;
import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import com.zeromail.core.referral.projection.ReferralDashboardQuery;
import com.zeromail.core.referral.projection.ReferralDashboardSnapshot;
import com.zeromail.core.referral.projection.ReferralLeaderboardRow;
import com.zeromail.core.referral.projection.ReferralTenantRankingSnapshot;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralDashboardQueryService {

    private final ReferralCampaignService referralCampaignService;
    private final ReferralDashboardReadRepository referralDashboardReadRepository;
    private final Clock clock;

    public ReferralDashboardQueryService(
            ReferralCampaignService referralCampaignService,
            ReferralDashboardReadRepository referralDashboardReadRepository,
            Clock clock) {
        this.referralCampaignService =
                Objects.requireNonNull(
                        referralCampaignService, "referralCampaignService must not be null");
        this.referralDashboardReadRepository =
                Objects.requireNonNull(
                        referralDashboardReadRepository,
                        "referralDashboardReadRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public ReferralDashboardSnapshot snapshot(ReferralDashboardQuery referralDashboardQuery) {
        ReferralCampaignSnapshot referralCampaign =
                referralCampaignService.campaign(referralDashboardQuery.campaignId());
        List<ReferralLeaderboardRow> leaderboard =
                referralDashboardReadRepository.leaderboard(referralDashboardQuery);
        ReferralLeaderboardRow currentTopTenant =
                leaderboard.isEmpty() ? null : leaderboard.getFirst();
        return new ReferralDashboardSnapshot(
                referralCampaign,
                referralDashboardReadRepository.totalSuccessfulReferrals(referralDashboardQuery),
                referralDashboardReadRepository.activeReferrerTenants(referralDashboardQuery),
                currentTopTenant,
                leaderboard,
                referralDashboardReadRepository.hourlyTimeSeries(referralDashboardQuery),
                clock.instant());
    }

    @Transactional(readOnly = true)
    public int successfulReferralsForTenant(UUID campaignId, UUID ownerTenantId) {
        return referralDashboardReadRepository.successfulReferralsForTenant(
                campaignId, ownerTenantId);
    }

    @Transactional(readOnly = true)
    public ReferralTenantRankingSnapshot tenantRanking(
            ReferralDashboardQuery referralDashboardQuery, UUID ownerTenantId) {
        Objects.requireNonNull(referralDashboardQuery, "referralDashboardQuery must not be null");
        Objects.requireNonNull(ownerTenantId, "ownerTenantId must not be null");
        List<ReferralLeaderboardRow> leaderboard =
                referralDashboardReadRepository.tenantLeaderboardWindow(
                        referralDashboardQuery, ownerTenantId);
        ReferralLeaderboardRow currentTenant =
                leaderboard.stream()
                        .filter(
                                referralLeaderboardRow ->
                                        referralLeaderboardRow.tenantId().equals(ownerTenantId))
                        .findFirst()
                        .orElse(null);
        return new ReferralTenantRankingSnapshot(
                leaderboard,
                currentTenant,
                referralDashboardReadRepository.rankedTenantCount(referralDashboardQuery),
                clock.instant());
    }
}
