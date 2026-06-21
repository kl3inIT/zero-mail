package com.zeromail.api.dto.admin.referral;

import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "campaignId",
            "name",
            "campaignCode",
            "slug",
            "status",
            "startsAt",
            "endsAt",
            "webBannerEnabled",
            "countdownEnabled",
            "leaderboardEnabled",
            "leaderboardLimit",
            "rewardRankCutoff",
            "rewardNotificationText",
            "bannerImageAvailable",
            "createdAt",
            "updatedAt"
        })
public record AdminReferralCampaignResponse(
        UUID campaignId,
        String name,
        String campaignCode,
        String slug,
        String description,
        @Schema(allowableValues = {"DRAFT", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED"}) String status,
        Instant startsAt,
        Instant endsAt,
        boolean webBannerEnabled,
        boolean countdownEnabled,
        boolean leaderboardEnabled,
        int leaderboardLimit,
        int rewardRankCutoff,
        String rewardNotificationText,
        boolean bannerImageAvailable,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminReferralCampaignResponse from(ReferralCampaignSnapshot snapshot) {
        return new AdminReferralCampaignResponse(
                snapshot.campaignId(),
                snapshot.name(),
                snapshot.campaignCode(),
                snapshot.slug(),
                snapshot.description(),
                snapshot.status().id(),
                snapshot.startsAt(),
                snapshot.endsAt(),
                snapshot.webBannerEnabled(),
                snapshot.countdownEnabled(),
                snapshot.leaderboardEnabled(),
                snapshot.leaderboardLimit(),
                snapshot.rewardRankCutoff(),
                snapshot.rewardNotificationText(),
                snapshot.bannerImageAvailable(),
                snapshot.createdAt(),
                snapshot.updatedAt());
    }
}
