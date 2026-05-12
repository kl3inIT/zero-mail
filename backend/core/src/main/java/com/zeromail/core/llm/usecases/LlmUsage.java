package com.zeromail.core.llm.usecases;

/** Vendor-neutral token usage and finish reason. */
public record LlmUsage(int promptTokens, int completionTokens, String finishReason) {

    public LlmUsage {
        finishReason = finishReason == null ? "unknown" : finishReason;
    }
}
