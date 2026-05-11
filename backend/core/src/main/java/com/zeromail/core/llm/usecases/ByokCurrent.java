package com.zeromail.core.llm.usecases;

import com.zeromail.core.llm.domain.BYOKProvider;
import java.time.Instant;

public record ByokCurrent(
        BYOKProvider provider, String endpointHost, String model, Instant savedAt) {}
