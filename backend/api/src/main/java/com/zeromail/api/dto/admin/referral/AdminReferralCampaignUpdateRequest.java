package com.zeromail.api.dto.admin.referral;

import com.zeromail.core.referral.usecases.ReferralCampaignUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(
        requiredProperties = {
            "name",
            "campaignCode",
            "slug",
            "startsAt",
            "endsAt",
            "webBannerEnabled",
            "countdownEnabled",
            "leaderboardEnabled",
            "leaderboardLimit",
            "rewardRankCutoff",
            "rewardNotificationText"
        })
public record AdminReferralCampaignUpdateRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 80) String campaignCode,
        @NotBlank @Size(max = 140) String slug,
        @Size(max = 500) String description,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull Boolean webBannerEnabled,
        @NotNull Boolean countdownEnabled,
        @NotNull Boolean leaderboardEnabled,
        @NotNull @Min(1) @Max(100) Integer leaderboardLimit,
        @NotNull @Min(1) @Max(100) Integer rewardRankCutoff,
        @NotBlank @Size(max = 500) String rewardNotificationText) {

    public ReferralCampaignUpdateCommand toCommand() {
        return new ReferralCampaignUpdateCommand(
                name,
                campaignCode,
                slug,
                description,
                startsAt,
                endsAt,
                Boolean.TRUE.equals(webBannerEnabled),
                Boolean.TRUE.equals(countdownEnabled),
                Boolean.TRUE.equals(leaderboardEnabled),
                leaderboardLimit,
                rewardRankCutoff,
                rewardNotificationText);
    }
}
