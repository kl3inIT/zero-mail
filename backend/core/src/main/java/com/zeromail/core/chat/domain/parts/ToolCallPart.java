package com.zeromail.core.chat.domain.parts;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolCallPart(
        String partId,
        String toolCallId,
        String toolName,
        String state,
        Map<String, Object> inputJson,
        boolean truncated)
        implements Part {

    public ToolCallPart {
        inputJson = immutableCopy(inputJson);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> inputJson) {
        if (inputJson == null || inputJson.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(inputJson));
    }
}
