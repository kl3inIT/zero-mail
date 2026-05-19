package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.sanitize.ToolOutputSanitizer;
import com.zeromail.core.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings({"unused", "DuplicatedCode"})
public class SanitizingSink implements ChatStreamSink {

    private static final Logger log = LoggerFactory.getLogger(SanitizingSink.class);

    private final ChatStreamSink downstreamSink;
    private final ToolOutputSanitizer toolOutputSanitizer;
    private final UUID chatId;
    private final ObjectMapper objectMapper;
    private final Map<String, String> toolNamesByCallId = new ConcurrentHashMap<>();

    public SanitizingSink(
            ChatStreamSink downstreamSink, ToolOutputSanitizer toolOutputSanitizer, UUID chatId) {
        this(downstreamSink, toolOutputSanitizer, chatId, JsonMapper.builder().build());
    }

    SanitizingSink(
            ChatStreamSink downstreamSink,
            ToolOutputSanitizer toolOutputSanitizer,
            UUID chatId,
            ObjectMapper objectMapper) {
        if (downstreamSink == null) {
            throw new IllegalArgumentException("downstreamSink must not be null");
        }
        if (toolOutputSanitizer == null) {
            throw new IllegalArgumentException("toolOutputSanitizer must not be null");
        }
        if (chatId == null) {
            throw new IllegalArgumentException("chatId must not be null");
        }
        this.downstreamSink = downstreamSink;
        this.toolOutputSanitizer = toolOutputSanitizer;
        this.chatId = chatId;
        this.objectMapper = objectMapper;
    }

    @Override
    public void emitTextStart(String partId) {
        downstreamSink.emitTextStart(partId);
    }

    @Override
    public void emitTextDelta(String partId, String tokenText) {
        downstreamSink.emitTextDelta(partId, tokenText);
    }

    @Override
    public void emitTextEnd(String partId) {
        downstreamSink.emitTextEnd(partId);
    }

    @Override
    public void emitToolInputStart(String toolCallId, String toolName) {
        toolNamesByCallId.put(toolCallId, toolName);
        downstreamSink.emitToolInputStart(toolCallId, toolName);
    }

    @Override
    public void emitToolInputAvailable(String toolCallId, String toolName, String inputJson) {
        toolNamesByCallId.put(toolCallId, toolName);
        downstreamSink.emitToolInputAvailable(toolCallId, toolName, inputJson);
    }

    @Override
    public void emitToolOutputAvailable(String toolCallId, String outputJson) {
        String toolName = toolNamesByCallId.getOrDefault(toolCallId, "unknown");
        Map<String, Object> outputMap = parseJsonObject(outputJson);
        ChatMessageParts sanitizedParts =
                toolOutputSanitizer.sanitize(
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolOutputPart(
                                                "tool-output-" + toolCallId,
                                                toolCallId,
                                                toolName,
                                                "output-available",
                                                outputMap,
                                                false))),
                        TenantContext.currentOptional().orElse("-"));
        Map<String, Object> sanitizedOutput =
                sanitizedParts.parts().stream()
                        .filter(ToolOutputPart.class::isInstance)
                        .map(ToolOutputPart.class::cast)
                        .findFirst()
                        .map(ToolOutputPart::outputJson)
                        .orElseGet(Map::of);
        log.info(
                "event=chat_sink_sanitized tenantId={} chatId={} toolName={}",
                TenantContext.currentOptional().orElse("-"),
                chatId,
                toolName);
        downstreamSink.emitToolOutputAvailable(toolCallId, writeJson(sanitizedOutput));
    }

    @Override
    public void emitDataPersistence(UUID chatMessageId, String state) {
        downstreamSink.emitDataPersistence(chatMessageId, state);
    }

    @Override
    public void emitFinish(String reason) {
        downstreamSink.emitFinish(reason);
    }

    @Override
    public void emitError(String code, String userFacingMessage) {
        downstreamSink.emitError(code, userFacingMessage);
    }

    @Override
    public void emitHeartbeat() {
        downstreamSink.emitHeartbeat();
    }

    private Map<String, Object> parseJsonObject(String json) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            Object javaValue = javaValue(jsonNode);
            if (javaValue instanceof Map<?, ?> valueMap) {
                Map<String, Object> typedMap = new LinkedHashMap<>();
                valueMap.forEach((key, value) -> typedMap.put(String.valueOf(key), value));
                return typedMap;
            }
            return javaValue == null ? Map.of("value", "") : Map.of("value", javaValue);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException("tool output JSON must be valid", jacksonException);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "tool output JSON could not be serialized", jacksonException);
        }
    }

    private Object javaValue(JsonNode jsonNode) {
        if (jsonNode.isObject()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            jsonNode.properties()
                    .forEach(
                            property ->
                                    fields.put(property.getKey(), javaValue(property.getValue())));
            return fields;
        }
        if (jsonNode.isArray()) {
            java.util.ArrayList<Object> values = new java.util.ArrayList<>();
            jsonNode.forEach(elementNode -> values.add(javaValue(elementNode)));
            return List.copyOf(values);
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
}
