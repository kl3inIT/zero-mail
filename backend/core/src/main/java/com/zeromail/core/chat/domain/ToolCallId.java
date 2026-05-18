package com.zeromail.core.chat.domain;

@SuppressWarnings("unused")
public record ToolCallId(String value) {

    public ToolCallId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tool call id value is required");
        }
    }
}
