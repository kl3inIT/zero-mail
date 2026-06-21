package com.zeromail.core.referral.usecases;

import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import java.time.Instant;
import java.util.Objects;

public record ReferralCampaignCreateCommand(
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
        String rewardNotificationText) {

    public ReferralCampaignCreateCommand {
        requireNonBlank(name, "name");
        requireNonBlank(campaignCode, "campaignCode");
        requireNonBlank(slug, "slug");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (leaderboardLimit < 1 || leaderboardLimit > 100) {
            throw new IllegalArgumentException("leaderboardLimit must be between 1 and 100");
        }
        if (rewardRankCutoff < 1 || rewardRankCutoff > 100) {
            throw new IllegalArgumentException("rewardRankCutoff must be between 1 and 100");
        }
        requireNonBlank(rewardNotificationText, "rewardNotificationText");
        if (rewardNotificationText.length() > 500) {
            throw new IllegalArgumentException(
                    "rewardNotificationText must be at most 500 characters");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
