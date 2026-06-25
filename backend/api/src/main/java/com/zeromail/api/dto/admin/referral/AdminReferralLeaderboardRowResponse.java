package com.zeromail.api.dto.admin.referral;

import com.zeromail.core.referral.projection.ReferralLeaderboardRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"tenantId", "tenantDisplayName", "successfulReferrals", "rank"})
public record AdminReferralLeaderboardRowResponse(
        UUID tenantId, String tenantDisplayName, int successfulReferrals, int rank) {

    public static AdminReferralLeaderboardRowResponse from(ReferralLeaderboardRow row) {
        return new AdminReferralLeaderboardRowResponse(
                row.tenantId(), row.tenantDisplayName(), row.successfulReferrals(), row.rank());
    }
}
