package com.zeromail.api.dto.referral;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Public, unauthenticated view of the currently-active referral campaign for the marketing landing
 * page banner. Returns only admin-published marketing copy (name, description, schedule, reward
 * text) — never tenant data, referral codes, or leaderboard rows. The authenticated, per-tenant
 * surface (code, rank, leaderboard) stays in {@link ReferralMeResponse}.
 *
 * <p>Only populated when a campaign is active right now AND the admin enabled {@code
 * webBannerEnabled}; otherwise {@link #inactive()} so the landing section hides itself.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"active"})
public record ReferralActiveCampaignResponse(
        boolean active,
        UUID campaignId,
        String name,
        String description,
        Instant startsAt,
        Instant endsAt,
        Boolean countdownEnabled,
        Boolean bannerImageAvailable,
        Integer rewardRankCutoff,
        String rewardNotificationText) {

    public static ReferralActiveCampaignResponse inactive() {
        return new ReferralActiveCampaignResponse(
                false, null, null, null, null, null, null, null, null, null);
    }

    public static ReferralActiveCampaignResponse active(ReferralCampaignSnapshot campaign) {
        return new ReferralActiveCampaignResponse(
                true,
                campaign.campaignId(),
                campaign.name(),
                campaign.description(),
                campaign.startsAt(),
                campaign.endsAt(),
                campaign.countdownEnabled(),
                campaign.bannerImageAvailable(),
                campaign.rewardRankCutoff(),
                campaign.rewardNotificationText());
    }
}
