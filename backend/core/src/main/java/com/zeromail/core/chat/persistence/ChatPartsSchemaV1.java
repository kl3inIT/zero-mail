package com.zeromail.core.chat.persistence;

import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.DataErrorPart;
import com.zeromail.core.chat.domain.parts.Part;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class ChatPartsSchemaV1 {

    private ChatPartsSchemaV1() {}

    public static ChatMessageParts deserialize(JsonNode rootNode) {
        JsonNode partsNode = rootNode.path("parts");
        if (!partsNode.isArray()) {
            throw new IllegalArgumentException("chat parts schema v1 requires parts array");
        }
        List<Part> parts = new ArrayList<>();
        for (JsonNode partNode : partsNode) {
            parts.add(deserializePart(partNode));
        }
        return ChatMessageParts.v1(parts);
    }

    public static Map<String, Object> serialize(ChatMessageParts chatMessageParts) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", ChatMessageParts.CURRENT_SCHEMA_VERSION);
        envelope.put(
                "parts",
                chatMessageParts.parts().stream().map(ChatPartsSchemaV1::serializePart).toList());
        return envelope;
    }

    private static Part deserializePart(JsonNode partNode) {
        String type = requiredText(partNode, "type");
        if ("text".equals(type)) {
            return new TextPart(optionalText(partNode, "partId"), requiredText(partNode, "text"));
        }
        if ("assistant-text".equals(type)) {
            return new AssistantTextPart(
                    optionalText(partNode, "partId"),
                    requiredText(partNode, "text"),
                    Instant.parse(requiredText(partNode, "completedAt")));
        }
        if (type.startsWith("tool-")) {
            String toolName = type.substring("tool-".length());
            String toolCallId = requiredText(partNode, "toolCallId");
            String state = optionalText(partNode, "state");
            Map<String, Object> inputJson = objectMap(partNode.path("input"));
            Map<String, Object> outputJson = objectMap(partNode.path("output"));
            Map<String, Object> confirmationJson = objectMap(partNode.path("confirmation"));
            boolean truncated = booleanValue(partNode.path("truncated"));
            if (!outputJson.isEmpty() || !confirmationJson.isEmpty()) {
                return new ToolOutputPart(
                        optionalText(partNode, "partId"),
                        toolCallId,
                        toolName,
                        state,
                        inputJson,
                        outputJson,
                        confirmationJson,
                        truncated);
            }
            return new ToolCallPart(
                    optionalText(partNode, "partId"),
                    toolCallId,
                    toolName,
                    state,
                    inputJson,
                    truncated);
        }
        if ("data-error".equals(type)) {
            return new DataErrorPart(
                    optionalText(partNode, "partId"),
                    optionalText(partNode, "toolCallId"),
                    optionalText(partNode, "toolName"),
                    requiredText(partNode, "errorMessage"));
        }
        throw new IllegalArgumentException("Unknown chat part type: " + type);
    }

    private static Map<String, Object> serializePart(Part part) {
        Map<String, Object> fields = new LinkedHashMap<>();
        switch (part) {
            case TextPart textPart -> {
                fields.put("type", "text");
                putIfPresent(fields, "partId", textPart.partId());
                fields.put("text", textPart.text());
            }
            case AssistantTextPart assistantTextPart -> {
                fields.put("type", "assistant-text");
                putIfPresent(fields, "partId", assistantTextPart.partId());
                fields.put("text", assistantTextPart.text());
                fields.put("completedAt", assistantTextPart.completedAt().toString());
            }
            case ToolCallPart toolCallPart -> {
                fields.put("type", "tool-" + toolCallPart.toolName());
                putIfPresent(fields, "partId", toolCallPart.partId());
                fields.put("toolCallId", toolCallPart.toolCallId());
                putIfPresent(fields, "state", toolCallPart.state());
                putIfNotEmpty(fields, "input", toolCallPart.inputJson());
                if (toolCallPart.truncated()) {
                    fields.put("truncated", true);
                }
            }
            case ToolOutputPart toolOutputPart -> {
                fields.put("type", "tool-" + toolOutputPart.toolName());
                putIfPresent(fields, "partId", toolOutputPart.partId());
                fields.put("toolCallId", toolOutputPart.toolCallId());
                putIfPresent(fields, "state", toolOutputPart.state());
                putIfNotEmpty(fields, "input", toolOutputPart.inputJson());
                putIfNotEmpty(fields, "confirmation", toolOutputPart.confirmationJson());
                putIfNotEmpty(fields, "output", toolOutputPart.outputJson());
                if (toolOutputPart.truncated()) {
                    fields.put("truncated", true);
                }
            }
            case DataErrorPart dataErrorPart -> {
                fields.put("type", "data-error");
                putIfPresent(fields, "partId", dataErrorPart.partId());
                putIfPresent(fields, "toolCallId", dataErrorPart.toolCallId());
                putIfPresent(fields, "toolName", dataErrorPart.toolName());
                fields.put("errorMessage", dataErrorPart.errorMessage());
            }
        }
        return fields;
    }

    private static Map<String, Object> objectMap(JsonNode jsonNode) {
        if (jsonNode.isMissingNode() || jsonNode.isNull()) {
            return Map.of();
        }
        if (!jsonNode.isObject()) {
            throw new IllegalArgumentException("tool payload must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) javaValue(jsonNode);
        return value;
    }

    private static Object javaValue(JsonNode jsonNode) {
        if (jsonNode.isObject()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> property : jsonNode.properties()) {
                fields.put(property.getKey(), javaValue(property.getValue()));
            }
            return fields;
        }
        if (jsonNode.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode elementNode : jsonNode) {
                values.add(javaValue(elementNode));
            }
            return values;
        }
        if (jsonNode.isNull()) {
            return null;
        }
        if (jsonNode.isBoolean()) {
            return jsonNode.asBoolean();
        }
        if (jsonNode.isNumber()) {
            return jsonNode.isIntegralNumber() ? jsonNode.asLong() : jsonNode.asDouble();
        }
        return jsonNode.asString();
    }

    private static String requiredText(JsonNode jsonNode, String fieldName) {
        String value = optionalText(jsonNode, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String optionalText(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        if (!fieldNode.isString()) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        return fieldNode.asString();
    }

    private static boolean booleanValue(JsonNode jsonNode) {
        return !jsonNode.isMissingNode() && !jsonNode.isNull() && jsonNode.asBoolean();
    }

    private static void putIfPresent(Map<String, Object> fields, String fieldName, String value) {
        if (value != null) {
            fields.put(fieldName, value);
        }
    }

    private static void putIfNotEmpty(
            Map<String, Object> fields, String fieldName, Map<String, Object> value) {
        if (value != null && !value.isEmpty()) {
            fields.put(fieldName, value);
        }
    }
}
