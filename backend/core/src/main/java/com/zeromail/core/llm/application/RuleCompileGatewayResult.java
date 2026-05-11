package com.zeromail.core.llm.application;

import java.util.Map;
import java.util.Objects;

public record RuleCompileGatewayResult(
        String toolName, String modelId, Map<String, Object> toolArguments) {

    public static final String TOOL_NAME = "rule_compile";

    public RuleCompileGatewayResult {
        Objects.requireNonNull(toolName, "toolName");
        if (toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        modelId = modelId == null || modelId.isBlank() ? null : modelId;
        toolArguments = toolArguments == null ? Map.of() : Map.copyOf(toolArguments);
    }
}
