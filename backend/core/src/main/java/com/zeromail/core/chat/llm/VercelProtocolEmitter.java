package com.zeromail.core.chat.llm;

import com.zeromail.core.chat.usecases.ChatStreamSink;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class VercelProtocolEmitter implements ChatStreamSink {

    private final FrameWriter frameWriter;
    private final ObjectMapper objectMapper;
    private final Set<String> openTextPartIds = ConcurrentHashMap.newKeySet();
    private final Set<String> completedTextPartIds = ConcurrentHashMap.newKeySet();
    private final Set<String> openToolCallIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean streamStarted = new AtomicBoolean(false);

    public VercelProtocolEmitter(FrameWriter frameWriter, ObjectMapper objectMapper) {
        this.frameWriter = frameWriter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void emitTextStart(String partId) {
        if (!openTextPartIds.add(partId)) {
            throw new IllegalStateException("text part already started: " + partId);
        }
        emit("text-start", fields("id", partId));
    }

    @Override
    public void emitTextDelta(String partId, String tokenText) {
        requireOpenTextPart(partId, "text delta");
        emit("text-delta", fields("id", partId, "delta", tokenText == null ? "" : tokenText));
    }

    @Override
    public void emitTextEnd(String partId) {
        requireOpenTextPart(partId, "text end");
        openTextPartIds.remove(partId);
        completedTextPartIds.add(partId);
        emit("text-end", fields("id", partId));
    }

    @Override
    public void emitToolInputStart(String toolCallId, String toolName) {
        if (!openToolCallIds.add(toolCallId)) {
            throw new IllegalStateException("tool input already started: " + toolCallId);
        }
        emit("tool-input-start", fields("toolCallId", toolCallId, "toolName", toolName));
    }

    @Override
    public void emitToolInputAvailable(String toolCallId, String toolName, String inputJson) {
        if (!openToolCallIds.contains(toolCallId)) {
            throw new IllegalStateException("tool input available before start: " + toolCallId);
        }
        emit(
                "tool-input-available",
                fields("toolCallId", toolCallId, "toolName", toolName, "input", inputJson));
    }

    @Override
    public void emitToolOutputAvailable(String toolCallId, String outputJson) {
        emit("tool-output-available", fields("toolCallId", toolCallId, "output", outputJson));
    }

    @Override
    public void emitDataPersistence(UUID chatMessageId, String state) {
        emit(
                "data-persistence",
                fields(
                        "id",
                        chatMessageId.toString(),
                        "data",
                        fields("chatMessageId", chatMessageId.toString(), "state", state)));
    }

    @Override
    public void emitFinish(String reason) {
        emit("finish", fields("finishReason", normalizeFinishReason(reason)));
    }

    @Override
    public void emitError(String code, String userFacingMessage) {
        emit(
                "error",
                fields(
                        "errorText",
                        userFacingMessage == null
                                ? "The assistant stream failed."
                                : userFacingMessage));
    }

    @Override
    public void emitHeartbeat() {
        writeFrame(": keepalive\n\n");
    }

    private void requireOpenTextPart(String partId, String operation) {
        if (!openTextPartIds.contains(partId) || completedTextPartIds.contains(partId)) {
            throw new IllegalStateException(operation + " before text start: " + partId);
        }
    }

    private void emit(String type, Map<String, Object> fields) {
        emitStartIfNeeded(type);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.putAll(fields);
        writeJsonFrame(payload);
    }

    private void emitStartIfNeeded(String type) {
        if ("start".equals(type) || !streamStarted.compareAndSet(false, true)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "start");
        writeJsonFrame(payload);
    }

    private void writeJsonFrame(Map<String, Object> payload) {
        try {
            writeFrame(objectMapper.writeValueAsString(payload));
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "stream event could not be serialized", jacksonException);
        }
    }

    private static String normalizeFinishReason(String reason) {
        if (reason == null || reason.isBlank() || "complete".equals(reason)) {
            return "stop";
        }
        if ("awaiting-confirmation".equals(reason) || "tool-call".equals(reason)) {
            return "tool-calls";
        }
        return switch (reason) {
            case "stop", "length", "content-filter", "tool-calls", "error", "other" -> reason;
            default -> "other";
        };
    }

    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            fields.put((String) keyValues[index], keyValues[index + 1]);
        }
        return fields;
    }

    private void writeFrame(String frame) {
        try {
            frameWriter.write(frame);
        } catch (IOException ioException) {
            throw new IllegalStateException("stream frame could not be sent", ioException);
        }
    }

    @FunctionalInterface
    public interface FrameWriter {
        void write(String frame) throws IOException;
    }
}
