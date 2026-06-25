package com.zeromail.api.dto.admin.referral;

import com.zeromail.core.referral.projection.ReferralTimeSeriesPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"bucketStart", "successfulReferrals"})
public record AdminReferralTimeSeriesPointResponse(Instant bucketStart, int successfulReferrals) {

    public static AdminReferralTimeSeriesPointResponse from(ReferralTimeSeriesPoint point) {
        return new AdminReferralTimeSeriesPointResponse(
                point.bucketStart(), point.successfulReferrals());
    }
}
