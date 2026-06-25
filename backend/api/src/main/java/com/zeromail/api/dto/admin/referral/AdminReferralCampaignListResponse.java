package com.zeromail.api.dto.admin.referral;

import com.zeromail.core.referral.projection.ReferralCampaignSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"campaigns"})
public record AdminReferralCampaignListResponse(List<AdminReferralCampaignResponse> campaigns) {

    public AdminReferralCampaignListResponse {
        campaigns = List.copyOf(campaigns);
    }

    public static AdminReferralCampaignListResponse from(List<ReferralCampaignSnapshot> campaigns) {
        return new AdminReferralCampaignListResponse(
                campaigns.stream().map(AdminReferralCampaignResponse::from).toList());
    }
}
