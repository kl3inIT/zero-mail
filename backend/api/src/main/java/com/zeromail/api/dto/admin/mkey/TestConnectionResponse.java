package com.zeromail.api.dto.admin.mkey;

import com.zeromail.core.llm.gateway.springai.admin.MasterKeyTestResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"result"})
public record TestConnectionResponse(
        @Schema(allowableValues = {"OK", "INVALID_KEY", "RATE_LIMITED", "NETWORK_ERROR", "TIMEOUT"})
                MasterKeyTestResult result) {}
