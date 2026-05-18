package com.zeromail.core.chat.domain.parts;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolOutputPart(
        String partId,
        String toolCallId,
        String toolName,
        String state,
        Map<String, Object> outputJson,
        boolean truncated)
        implements Part {

    public ToolOutputPart {
        outputJson = immutableCopy(outputJson);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> outputJson) {
        if (outputJson == null || outputJson.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(outputJson));
    }
}
