package com.zeromail.core.chat.llm;

import com.zeromail.core.chat.usecases.RawToolCall;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatToolCallRegistry {

    private final Map<String, RawToolCall> rawToolCallsById = new LinkedHashMap<>();

    public void appendDelta(String toolCallId, String toolName, String argsChunkDelta) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId must not be blank");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        RawToolCall rawToolCall =
                rawToolCallsById.computeIfAbsent(
                        toolCallId,
                        ignored ->
                                new RawToolCall(toolCallId, toolName, new StringBuilder(), false));
        rawToolCall.argsDeltaAccumulator().append(argsChunkDelta == null ? "" : argsChunkDelta);
    }

    public List<RawToolCall> finalizeToolCalls() {
        return rawToolCallsById.values().stream().map(RawToolCall::asFinalized).toList();
    }
}
