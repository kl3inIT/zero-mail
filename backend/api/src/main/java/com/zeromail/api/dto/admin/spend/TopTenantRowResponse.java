package com.zeromail.api.dto.admin.spend;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.spend.projection.TopTenantRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(
        requiredProperties = {
            "gmailAccountEmailOrPlaceholder",
            "totalCost",
            "unknownCost",
            "callCount",
            "isKAnonymized",
            "unknownPct"
        })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopTenantRowResponse(
        UUID tenantId,
        String gmailAccountEmailOrPlaceholder,
        BigDecimal totalCost,
        BigDecimal unknownCost,
        int callCount,
        boolean isKAnonymized,
        double unknownPct) {

    public static TopTenantRowResponse from(TopTenantRow row) {
        return new TopTenantRowResponse(
                row.tenantId(),
                row.gmailAccountEmailOrPlaceholder(),
                row.totalCost(),
                row.unknownCost(),
                row.callCount(),
                row.isKAnonymized(),
                row.unknownPct());
    }
}
