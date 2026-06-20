package com.zeromail.api.dto.admin.referral;

import com.zeromail.core.referral.domain.ReferralCampaignStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"status"})
public record AdminReferralCampaignStatusUpdateRequest(
        @NotNull @Schema(allowableValues = {"DRAFT", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED"})
                String status) {

    public ReferralCampaignStatus statusValue() {
        return ReferralCampaignStatus.fromId(status);
    }
}
