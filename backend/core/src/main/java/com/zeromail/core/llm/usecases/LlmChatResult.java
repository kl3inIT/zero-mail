package com.zeromail.core.llm.usecases;

import java.util.List;
import java.util.Objects;

/** Vendor-neutral chat result crossing the LlmModelClient seam. */
public record LlmChatResult(List<RawToolCall> toolCalls, LlmUsage usage, String assistantText) {

    public LlmChatResult(List<RawToolCall> toolCalls, LlmUsage usage) {
        this(toolCalls, usage, "");
    }

    public LlmChatResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        Objects.requireNonNull(usage, "usage");
        assistantText = assistantText == null ? "" : assistantText;
    }
}
