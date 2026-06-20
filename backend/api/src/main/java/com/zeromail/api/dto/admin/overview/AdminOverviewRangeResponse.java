package com.zeromail.api.dto.admin.overview;

import com.zeromail.core.admin.overview.projection.AdminOverviewRange;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"from", "to"})
public record AdminOverviewRangeResponse(Instant from, Instant to) {

    public static AdminOverviewRangeResponse from(AdminOverviewRange range) {
        return new AdminOverviewRangeResponse(range.from(), range.to());
    }
}
