package com.zeromail.core.llm.model;

import java.util.Map;
import java.util.Objects;

/**
 * Project-local tool descriptor crossing the LlmGateway public surface.
 */
public record LlmTool(String name, String description, Map<String, Object> jsonSchema) {

    public LlmTool {
        Objects.requireNonNull(name, "name");
        jsonSchema = jsonSchema == null ? Map.of() : Map.copyOf(jsonSchema);
    }
}
