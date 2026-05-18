package com.zeromail.core.chat.domain.parts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolOutputPart(
        String partId,
        String toolCallId,
        String toolName,
        String state,
        Map<String, Object> inputJson,
        Map<String, Object> outputJson,
        Map<String, Object> confirmationJson,
        boolean truncated)
        implements Part {

    public ToolOutputPart(
            String partId,
            String toolCallId,
            String toolName,
            String state,
            Map<String, Object> outputJson,
            boolean truncated) {
        this(partId, toolCallId, toolName, state, Map.of(), outputJson, Map.of(), truncated);
    }

    public ToolOutputPart {
        inputJson = immutableCopy(inputJson);
        outputJson = immutableCopy(outputJson);
        confirmationJson = immutableCopy(confirmationJson);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> outputJson) {
        if (outputJson == null || outputJson.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(outputJson));
    }
}
