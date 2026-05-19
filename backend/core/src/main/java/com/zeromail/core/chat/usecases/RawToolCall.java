package com.zeromail.core.chat.usecases;

public record RawToolCall(
        String toolCallId, String toolName, StringBuilder argsDeltaAccumulator, boolean finalized) {

    public RawToolCall {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId must not be blank");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        argsDeltaAccumulator =
                argsDeltaAccumulator == null ? new StringBuilder() : argsDeltaAccumulator;
    }

    public String argsJson() {
        return argsDeltaAccumulator.toString();
    }

    public RawToolCall asFinalized() {
        return new RawToolCall(toolCallId, toolName, new StringBuilder(argsJson()), true);
    }
}
