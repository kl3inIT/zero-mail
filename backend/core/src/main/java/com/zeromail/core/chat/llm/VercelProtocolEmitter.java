package com.zeromail.core.chat.llm;

import com.zeromail.core.chat.usecases.ChatStreamSink;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class VercelProtocolEmitter implements ChatStreamSink {

    private final FrameWriter frameWriter;
    private final ObjectMapper objectMapper;
    private final Set<String> openTextPartIds = ConcurrentHashMap.newKeySet();
    private final Set<String> completedTextPartIds = ConcurrentHashMap.newKeySet();
    private final Set<String> openToolCallIds = ConcurrentHashMap.newKeySet();

    public VercelProtocolEmitter(FrameWriter frameWriter) {
        this(frameWriter, JsonMapper.builder().build());
    }

    VercelProtocolEmitter(FrameWriter frameWriter, ObjectMapper objectMapper) {
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
        emit("data-persistence", fields("chatMessageId", chatMessageId.toString(), "state", state));
    }

    @Override
    public void emitFinish(String reason) {
        emit("finish", fields("reason", reason == null ? "stop" : reason));
    }

    @Override
    public void emitError(String code, String userFacingMessage) {
        emit(
                "error",
                fields(
                        "code",
                        code == null ? "chat_stream_error" : code,
                        "message",
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

    private void emit(String type, Map<String, String> fields) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.putAll(fields);
        try {
            writeFrame(objectMapper.writeValueAsString(payload));
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "stream event could not be serialized", jacksonException);
        }
    }

    private static Map<String, String> fields(String... keyValues) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            fields.put(keyValues[index], keyValues[index + 1]);
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
