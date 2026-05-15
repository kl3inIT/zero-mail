package com.zeromail.core.llm.gateway.springai;

import com.zeromail.core.llm.usecases.LlmTool;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * One-time JSON serialization of LLM tool input schemas. The Spring AI tool callback API requires a
 * String schema per tool per call; with a finite, allow-listed tool set the serialized form is
 * stable for the JVM lifetime, so we cache by tool name.
 */
final class LlmToolJsonSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentMap<String, String> CACHE = new ConcurrentHashMap<>();

    private LlmToolJsonSchemas() {}

    static String jsonSchemaOf(LlmTool tool) {
        return CACHE.computeIfAbsent(tool.name(), name -> serialize(tool));
    }

    private static String serialize(LlmTool tool) {
        try {
            return MAPPER.writeValueAsString(tool.jsonSchema());
        } catch (JacksonException jsonSerializationFailure) {
            throw new IllegalStateException(
                    "Unable to serialize LLM tool schema", jsonSerializationFailure);
        }
    }
}
