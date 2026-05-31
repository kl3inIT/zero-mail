package com.zeromail.api.dto.byok;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.llm.byok.ByokRowSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"provider", "baseUrl", "lastFourChars", "active"})
public record ByokResponse(
        @Schema(allowableValues = {"OPENAI", "ANTHROPIC", "GOOGLE", "DEEPSEEK"}) String provider,
        String baseUrl,
        String lastFourChars,
        String modelId,
        boolean active,
        @Schema(allowableValues = {"OK", "INVALID_KEY", "RATE_LIMITED", "NETWORK_ERROR", "TIMEOUT"})
                String lastTestResult,
        Instant lastTestedAt) {

    public static ByokResponse from(ByokRowSummary byokRowSummary) {
        return new ByokResponse(
                byokRowSummary.provider(),
                byokRowSummary.baseUrl(),
                byokRowSummary.lastFourChars(),
                byokRowSummary.modelId(),
                byokRowSummary.active(),
                byokRowSummary.lastTestResult(),
                byokRowSummary.lastTestedAt());
    }
}
