package com.zeromail.core.llm.model;

import java.time.Instant;

public record ByokCurrent(BYOKProvider provider, String endpointHost, String model, Instant savedAt) {}
