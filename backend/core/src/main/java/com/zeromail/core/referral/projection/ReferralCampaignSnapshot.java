package com.zeromail.core.referral.projection;

import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReferralCampaignSnapshot(
        UUID campaignId,
        String name,
        String campaignCode,
        String slug,
        String description,
        ReferralCampaignStatus status,
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

    public ReferralCampaignSnapshot {
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(campaignCode, "campaignCode must not be null");
        Objects.requireNonNull(slug, "slug must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        Objects.requireNonNull(rewardNotificationText, "rewardNotificationText must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
