package com.zeromail.core.llm.application;

import java.util.List;
import java.util.Objects;

/**
 * Vendor-neutral chat result crossing the LlmModelClient seam.
 */
public record LlmChatResult(List<RawToolCall> toolCalls, LlmUsage usage) {

    public LlmChatResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        Objects.requireNonNull(usage, "usage");
    }
}
