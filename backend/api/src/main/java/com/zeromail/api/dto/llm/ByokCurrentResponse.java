package com.zeromail.api.dto.llm;

import java.time.Instant;

import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.llm.model.ByokCurrent;

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
