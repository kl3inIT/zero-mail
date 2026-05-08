package com.zeromail.api.dto.llm;

import java.time.Instant;

import com.zeromail.core.llm.model.BYOKProvider;

public record ByokCurrentResponse(
    BYOKProvider provider, String endpointHost, String model, Instant savedAt) {}
