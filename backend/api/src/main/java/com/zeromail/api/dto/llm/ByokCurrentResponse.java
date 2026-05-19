package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.usecases.ByokCurrent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"provider", "endpointHost", "model", "savedAt"})
public record ByokCurrentResponse(
        @Schema(nullable = true) BYOKProvider provider,
        @Schema(nullable = true) String endpointHost,
        @Schema(nullable = true) String model,
        @Schema(nullable = true) Instant savedAt) {

    public static ByokCurrentResponse from(ByokCurrent current) {
        return new ByokCurrentResponse(
                current.provider(), current.endpointHost(), current.model(), current.savedAt());
    }

    public static ByokCurrentResponse empty() {
        return new ByokCurrentResponse(null, null, null, null);
    }
}
