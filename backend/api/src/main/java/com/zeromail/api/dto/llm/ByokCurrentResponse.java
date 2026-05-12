package com.zeromail.api.dto.llm;

import com.zeromail.core.llm.domain.BYOKProvider;
import com.zeromail.core.llm.usecases.ByokCurrent;
import java.time.Instant;

public record ByokCurrentResponse(
        BYOKProvider provider, String endpointHost, String model, Instant savedAt) {

    public static ByokCurrentResponse from(ByokCurrent current) {
        return new ByokCurrentResponse(
                current.provider(), current.endpointHost(), current.model(), current.savedAt());
    }

    public static ByokCurrentResponse empty() {
        return new ByokCurrentResponse(null, null, null, null);
    }
}
