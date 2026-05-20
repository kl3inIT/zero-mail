package com.zeromail.api.dto.admin.spend;

import com.zeromail.core.admin.spend.projection.ProviderStackBarRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(
        requiredProperties = {
            "bucketDate",
            "provider",
            "platformCost",
            "byokCost",
            "unknownCost",
            "callCount"
        })
public record ProviderStackBarRowResponse(
        Instant bucketDate,
        String provider,
        BigDecimal platformCost,
        BigDecimal byokCost,
        BigDecimal unknownCost,
        int callCount) {

    public static ProviderStackBarRowResponse from(ProviderStackBarRow row) {
        return new ProviderStackBarRowResponse(
                row.bucketDate(),
                row.provider(),
                row.platformCost(),
                row.byokCost(),
                row.unknownCost(),
                row.callCount());
    }
}
