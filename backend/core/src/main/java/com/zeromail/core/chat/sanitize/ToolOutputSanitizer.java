package com.zeromail.core.chat.sanitize;

import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.Part;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
public class ToolOutputSanitizer {

    private static final Logger log = LoggerFactory.getLogger(ToolOutputSanitizer.class);
    private static final int STRING_LENGTH_CAP = 4000;
    private static final Set<String> EMAIL_READ_TOOLS =
            Set.of("searchInbox", "getMessage", "getThread", "listLabels");
    private static final Set<String> DRAFT_TOOLS =
            Set.of("sendEmail", "replyEmail", "forwardEmail", "saveDraft");

    private final List<ToolSanitizerStage> stages;

    public ToolOutputSanitizer() {
        List<ToolSanitizerStage> configuredStages =
                new ArrayList<>(
                        List.of(
                                new EmailBodyStripStage(),
                                new LengthCapStage(),
                                new SchemaVersionStampStage()));
        AnnotationAwareOrderComparator.sort(configuredStages);
        this.stages = List.copyOf(configuredStages);
    }

    public ChatMessageParts sanitize(ChatMessageParts inputParts, String tenantContextForLogging) {
        SanitizerState currentState =
                new SanitizerState(
                        inputParts == null ? ChatMessageParts.v1(List.of()) : inputParts,
                        List.of());
        for (ToolSanitizerStage stage : stages) {
            currentState = stage.apply(currentState);
        }
        currentState
                .toolLogEntries()
                .forEach(entry -> logSanitizedTool(tenantContextForLogging, entry));
        return currentState.chatMessageParts();
    }

    private static void logSanitizedTool(String tenantContextForLogging, ToolLogEntry entry) {
        log.info(
                "event=chat_tool_output_sanitized tenantId={} chatId={} toolName={} toolSource={} truncated={} signatureMatches={}",
                tenantContextForLogging,
                "-",
                entry.toolName(),
                entry.toolSource().logValue(),
                entry.truncated(),
                entry.signatureMatches());
    }

    private interface ToolSanitizerStage {
        SanitizerState apply(SanitizerState state);
    }

    @Order(1)
    private static final class EmailBodyStripStage implements ToolSanitizerStage {

        @Override
        public SanitizerState apply(SanitizerState state) {
            List<Part> sanitizedParts = new ArrayList<>();
            List<ToolLogEntry> logEntries = new ArrayList<>(state.toolLogEntries());
            for (Part part : state.chatMessageParts().parts()) {
                if (part
                        instanceof
                        ToolCallPart(
                                String partId,
                                String toolCallId,
                                String toolName,
                                String stateValue,
                                Map<String, Object> inputJson,
                                boolean truncated)) {
                    ToolSource toolSource = ToolSource.from(toolName);
                    boolean stripBodyFields = toolSource.stripBodyFields();
                    SanitizedJson sanitizedInput = sanitizeJson(inputJson, stripBodyFields, false);
                    sanitizedParts.add(
                            new ToolCallPart(
                                    partId,
                                    toolCallId,
                                    toolName,
                                    stateValue,
                                    asMap(sanitizedInput.value()),
                                    truncated));
                    logEntries.add(
                            new ToolLogEntry(
                                    toolName,
                                    toolSource,
                                    false,
                                    sanitizedInput.signatureMatches()));
                } else if (part
                        instanceof
                        ToolOutputPart(
                                String partId,
                                String toolCallId,
                                String toolName,
                                String stateValue,
                                Map<String, Object> inputJson,
                                Map<String, Object> outputJson,
                                Map<String, Object> confirmationJson,
                                boolean truncated)) {
                    ToolSource toolSource = ToolSource.from(toolName);
                    boolean stripBodyFields = toolSource.stripBodyFields();
                    SanitizedJson sanitizedInput = sanitizeJson(inputJson, stripBodyFields, false);
                    SanitizedJson sanitizedOutput =
                            sanitizeJson(outputJson, stripBodyFields, false);
                    SanitizedJson sanitizedConfirmation =
                            sanitizeJson(confirmationJson, stripBodyFields, false);
                    sanitizedParts.add(
                            new ToolOutputPart(
                                    partId,
                                    toolCallId,
                                    toolName,
                                    stateValue,
                                    asMap(sanitizedInput.value()),
                                    asMap(sanitizedOutput.value()),
                                    asMap(sanitizedConfirmation.value()),
                                    truncated));
                    logEntries.add(
                            new ToolLogEntry(
                                    toolName,
                                    toolSource,
                                    false,
                                    sanitizedInput.signatureMatches()
                                            + sanitizedOutput.signatureMatches()
                                            + sanitizedConfirmation.signatureMatches()));
                } else {
                    sanitizedParts.add(part);
                }
            }
            return new SanitizerState(
                    new ChatMessageParts(state.chatMessageParts().schemaVersion(), sanitizedParts),
                    logEntries);
        }
    }

    @Order(2)
    private static final class LengthCapStage implements ToolSanitizerStage {

        @Override
        public SanitizerState apply(SanitizerState state) {
            List<Part> sanitizedParts = new ArrayList<>();
            List<ToolLogEntry> updatedLogEntries = new ArrayList<>();
            int toolPartIndex = 0;
            for (Part part : state.chatMessageParts().parts()) {
                if (part
                        instanceof
                        ToolCallPart(
                                String partId,
                                String toolCallId,
                                String toolName,
                                String stateValue,
                                Map<String, Object> inputJson,
                                boolean truncated)) {
                    SanitizedJson sanitizedInput = sanitizeJson(inputJson, false, true);
                    sanitizedParts.add(
                            new ToolCallPart(
                                    partId,
                                    toolCallId,
                                    toolName,
                                    stateValue,
                                    asMap(sanitizedInput.value()),
                                    truncated || sanitizedInput.truncated()));
                    updatedLogEntries.add(
                            state.toolLogEntries()
                                    .get(toolPartIndex)
                                    .withTruncated(sanitizedInput.truncated()));
                    toolPartIndex++;
                } else if (part
                        instanceof
                        ToolOutputPart(
                                String partId,
                                String toolCallId,
                                String toolName,
                                String stateValue,
                                Map<String, Object> inputJson,
                                Map<String, Object> outputJson,
                                Map<String, Object> confirmationJson,
                                boolean truncated)) {
                    SanitizedJson sanitizedInput = sanitizeJson(inputJson, false, true);
                    SanitizedJson sanitizedOutput = sanitizeJson(outputJson, false, true);
                    SanitizedJson sanitizedConfirmation =
                            sanitizeJson(confirmationJson, false, true);
                    sanitizedParts.add(
                            new ToolOutputPart(
                                    partId,
                                    toolCallId,
                                    toolName,
                                    stateValue,
                                    asMap(sanitizedInput.value()),
                                    asMap(sanitizedOutput.value()),
                                    asMap(sanitizedConfirmation.value()),
                                    truncated
                                            || sanitizedInput.truncated()
                                            || sanitizedOutput.truncated()
                                            || sanitizedConfirmation.truncated()));
                    updatedLogEntries.add(
                            state.toolLogEntries()
                                    .get(toolPartIndex)
                                    .withTruncated(
                                            sanitizedInput.truncated()
                                                    || sanitizedOutput.truncated()
                                                    || sanitizedConfirmation.truncated()));
                    toolPartIndex++;
                } else {
                    sanitizedParts.add(part);
                }
            }
            return new SanitizerState(
                    new ChatMessageParts(state.chatMessageParts().schemaVersion(), sanitizedParts),
                    updatedLogEntries);
        }
    }

    @Order(3)
    private static final class SchemaVersionStampStage implements ToolSanitizerStage {

        @Override
        public SanitizerState apply(SanitizerState state) {
            return new SanitizerState(
                    new ChatMessageParts(
                            ChatMessageParts.CURRENT_SCHEMA_VERSION,
                            state.chatMessageParts().parts()),
                    state.toolLogEntries());
        }
    }

    private static SanitizedJson sanitizeJson(
            Object candidate, boolean stripBodyFields, boolean capStringValues) {
        if (candidate instanceof Map<?, ?> candidateMap) {
            Map<String, Object> sanitizedMap = new LinkedHashMap<>();
            boolean truncated = false;
            int signatureMatches = 0;
            for (Map.Entry<?, ?> entry : candidateMap.entrySet()) {
                String fieldName = String.valueOf(entry.getKey());
                if (stripBodyFields && BodyContentSignatures.matches(fieldName)) {
                    signatureMatches++;
                    continue;
                }
                SanitizedJson sanitizedChild =
                        sanitizeJson(entry.getValue(), stripBodyFields, capStringValues);
                sanitizedMap.put(fieldName, sanitizedChild.value());
                truncated = truncated || sanitizedChild.truncated();
                signatureMatches += sanitizedChild.signatureMatches();
            }
            return new SanitizedJson(
                    Collections.unmodifiableMap(sanitizedMap), truncated, signatureMatches);
        }
        if (candidate instanceof List<?> candidateList) {
            List<Object> sanitizedList = new ArrayList<>();
            boolean truncated = false;
            int signatureMatches = 0;
            for (Object value : candidateList) {
                SanitizedJson sanitizedChild =
                        sanitizeJson(value, stripBodyFields, capStringValues);
                sanitizedList.add(sanitizedChild.value());
                truncated = truncated || sanitizedChild.truncated();
                signatureMatches += sanitizedChild.signatureMatches();
            }
            return new SanitizedJson(List.copyOf(sanitizedList), truncated, signatureMatches);
        }
        if (candidate instanceof String text
                && capStringValues
                && text.length() > STRING_LENGTH_CAP) {
            return new SanitizedJson(text.substring(0, STRING_LENGTH_CAP), true, 0);
        }
        return new SanitizedJson(candidate, false, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> valueMap) {
            return (Map<String, Object>) valueMap;
        }
        return Map.of();
    }

    private enum ToolSource {
        READ("read", true),
        DRAFT("draft", false),
        OTHER("other", true);

        private final String logValue;
        private final boolean stripBodyFields;

        ToolSource(String logValue, boolean stripBodyFields) {
            this.logValue = logValue;
            this.stripBodyFields = stripBodyFields;
        }

        static ToolSource from(String toolName) {
            if (EMAIL_READ_TOOLS.contains(toolName)) {
                return READ;
            }
            if (DRAFT_TOOLS.contains(toolName)) {
                return DRAFT;
            }
            return OTHER;
        }

        String logValue() {
            return logValue;
        }

        boolean stripBodyFields() {
            return stripBodyFields;
        }
    }

    private record SanitizerState(
            ChatMessageParts chatMessageParts, List<ToolLogEntry> toolLogEntries) {}

    private record SanitizedJson(Object value, boolean truncated, int signatureMatches) {}

    private record ToolLogEntry(
            String toolName, ToolSource toolSource, boolean truncated, int signatureMatches) {

        ToolLogEntry withTruncated(boolean nextTruncated) {
            return new ToolLogEntry(
                    toolName, toolSource, truncated || nextTruncated, signatureMatches);
        }
    }
}
