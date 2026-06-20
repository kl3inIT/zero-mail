package com.zeromail.api.dto.referral;

import com.zeromail.core.referral.projection.ReferralLeaderboardRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "tenantId",
            "tenantDisplayName",
            "successfulReferrals",
            "rank",
            "currentTenant"
        })
public record ReferralLeaderboardRowResponse(
        UUID tenantId,
        String tenantDisplayName,
        int successfulReferrals,
        int rank,
        boolean currentTenant) {

    public static ReferralLeaderboardRowResponse from(
            ReferralLeaderboardRow row, UUID currentTenantId) {
        return new ReferralLeaderboardRowResponse(
                row.tenantId(),
                row.tenantDisplayName(),
                row.successfulReferrals(),
                row.rank(),
                row.tenantId().equals(currentTenantId));
    }
}
