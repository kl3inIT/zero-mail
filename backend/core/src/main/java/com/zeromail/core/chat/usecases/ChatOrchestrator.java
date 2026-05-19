package com.zeromail.core.chat.usecases;

import com.zeromail.core.chat.domain.ChatId;
import com.zeromail.core.chat.domain.ChatMessage;
import com.zeromail.core.chat.domain.ChatMessageId;
import com.zeromail.core.chat.domain.ChatRole;
import com.zeromail.core.chat.domain.ChatToolName;
import com.zeromail.core.chat.domain.ToolCategory;
import com.zeromail.core.chat.domain.parts.AssistantTextPart;
import com.zeromail.core.chat.domain.parts.ChatMessageParts;
import com.zeromail.core.chat.domain.parts.Part;
import com.zeromail.core.chat.domain.parts.TextPart;
import com.zeromail.core.chat.domain.parts.ToolCallPart;
import com.zeromail.core.chat.domain.parts.ToolOutputPart;
import com.zeromail.core.chat.llm.TenantAwareReactorScheduler;
import com.zeromail.core.chat.llm.springai.ZeroMailChatMemory;
import com.zeromail.core.chat.persistence.ChatMessageJdbcRepository;
import com.zeromail.core.chat.sanitize.ToolOutputSanitizer;
import com.zeromail.core.chat.sanitize.XmlFencedPersonalizationRenderer;
import com.zeromail.core.chat.usecases.tools.ChatReadToolHandler;
import com.zeromail.core.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@SuppressWarnings("SqlResolve")
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);
    private static final int TITLE_MAX_LENGTH = 40;

    private final ChatLlmGateway chatLlmGateway;
    private final ZeroMailChatMemory zeroMailChatMemory;
    private final ToolOutputSanitizer toolOutputSanitizer;
    private final TenantAwareReactorScheduler tenantAwareReactorScheduler;
    private final XmlFencedPersonalizationRenderer personalizationRenderer;
    private final ChatMessageJdbcRepository chatMessageRepository;
    private final ChatToolCatalog chatToolCatalog;
    private final ZeroMailChatProperties chatProperties;
    private final LlmTokenizer llmTokenizer;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<ChatToolName, ChatReadToolHandler> readToolHandlers;

    public ChatOrchestrator(
            ChatLlmGateway chatLlmGateway,
            ZeroMailChatMemory zeroMailChatMemory,
            ToolOutputSanitizer toolOutputSanitizer,
            TenantAwareReactorScheduler tenantAwareReactorScheduler,
            XmlFencedPersonalizationRenderer personalizationRenderer,
            ChatMessageJdbcRepository chatMessageRepository,
            ChatToolCatalog chatToolCatalog,
            ZeroMailChatProperties chatProperties,
            LlmTokenizer llmTokenizer,
            TransactionTemplate transactionTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            List<ChatReadToolHandler> readToolHandlers) {
        this.chatLlmGateway = chatLlmGateway;
        this.zeroMailChatMemory = zeroMailChatMemory;
        this.toolOutputSanitizer = toolOutputSanitizer;
        this.tenantAwareReactorScheduler = tenantAwareReactorScheduler;
        this.personalizationRenderer = personalizationRenderer;
        this.chatMessageRepository = chatMessageRepository;
        this.chatToolCatalog = chatToolCatalog;
        this.chatProperties = chatProperties;
        this.llmTokenizer = llmTokenizer;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.readToolHandlers = readToolHandlerMap(readToolHandlers);
    }

    public Disposable stream(ChatStreamCommand command, ChatStreamSink streamSink) {
        UUID tenantId = TenantContext.currentTenantUuid();
        if (!tenantId.toString().equals(command.tenantId())) {
            throw new IllegalStateException("tenant context does not match chat command");
        }
        PreparedTurn preparedTurn =
                transactionTemplate.execute(
                        _ -> prepareTurn(tenantId, command.chatId(), command.userText()));
        streamSink.emitDataPersistence(preparedTurn.userMessageId(), "user-message-saved");

        SanitizingSink sanitizingSink =
                new SanitizingSink(streamSink, toolOutputSanitizer, preparedTurn.chatId());
        Scheduler streamScheduler = tenantAwareReactorScheduler.scheduler();
        TrackingDisposable trackingDisposable = new TrackingDisposable(streamScheduler);
        trackingDisposable.add(
                streamScheduler.schedule(
                        () -> {
                            try {
                                executeReadToolLoop(
                                        tenantId,
                                        preparedTurn,
                                        command,
                                        sanitizingSink,
                                        trackingDisposable);
                            } catch (RuntimeException runtimeException) {
                                if (!trackingDisposable.isDisposed()) {
                                    log.warn(
                                            "event=chat_stream_failed tenantId={} chatId={} errorClass={}",
                                            tenantId,
                                            preparedTurn.chatId(),
                                            rootCause(runtimeException).getClass().getSimpleName());
                                    log.debug(
                                            "event=chat_stream_failed_stacktrace tenantId={} chatId={}",
                                            tenantId,
                                            preparedTurn.chatId(),
                                            runtimeException);
                                    streamSink.emitError(
                                            "chat_stream_failed", "The assistant stream failed.");
                                }
                            } finally {
                                streamScheduler.dispose();
                            }
                        }));
        return trackingDisposable;
    }

    private void executeReadToolLoop(
            UUID tenantId,
            PreparedTurn preparedTurn,
            ChatStreamCommand command,
            ChatStreamSink streamSink,
            TrackingDisposable trackingDisposable) {
        for (int attempt = 0; attempt < 4 && !trackingDisposable.isDisposed(); attempt++) {
            InterceptingSink interceptingSink = new InterceptingSink(streamSink);
            ChatStreamRequest streamRequest =
                    new ChatStreamRequest(
                            tenantId.toString(),
                            preparedTurn.chatId(),
                            command.modelOverride() == null || command.modelOverride().isBlank()
                                    ? chatProperties.defaultModel()
                                    : command.modelOverride(),
                            personalizationRenderer.render(tenantId.toString()),
                            chatToolCatalog,
                            history(preparedTurn.chatId()));
            trackingDisposable.add(chatLlmGateway.streamChat(streamRequest, interceptingSink));
            TurnOutcome outcome = awaitOutcome(interceptingSink, trackingDisposable);
            if (outcome == null) {
                return;
            }
            if (outcome.toolName() != null && outcome.toolName().category() == ToolCategory.READ) {
                executeReadTool(tenantId, preparedTurn.chatId(), outcome, streamSink);
                continue;
            }
            if (outcome.toolName() != null) {
                UUID toolCallMessageId =
                        transactionTemplate.execute(
                                _ ->
                                        persistToolCall(
                                                preparedTurn.chatId(),
                                                tenantId,
                                                outcome.toolCallId(),
                                                outcome.toolName().id(),
                                                outcome.toolInputJson()));
                streamSink.emitDataPersistence(toolCallMessageId, "tool-call-saved");
                streamSink.emitFinish("awaiting-confirmation");
                return;
            }
            if (!outcome.assistantText().isBlank()) {
                UUID assistantMessageId =
                        transactionTemplate.execute(
                                _ ->
                                        persistAssistantText(
                                                preparedTurn.chatId(),
                                                tenantId,
                                                outcome.assistantText()));
                streamSink.emitDataPersistence(assistantMessageId, "assistant-message-saved");
            }
            streamSink.emitFinish("complete");
            return;
        }
        streamSink.emitFinish("complete");
    }

    private List<ChatMessage> history(UUID chatId) {
        zeroMailChatMemory.get(chatId.toString(), chatProperties.maxHistoryTokens());
        return budgetHistory(
                chatMessageRepository.findByChatIdOrderByCreatedAtAsc(chatId),
                chatProperties.maxHistoryTokens());
    }

    private void executeReadTool(
            UUID tenantId, UUID chatId, TurnOutcome outcome, ChatStreamSink streamSink) {
        ChatReadToolHandler readToolHandler = readToolHandlers.get(outcome.toolName());
        if (readToolHandler == null) {
            throw new IllegalStateException("No read tool handler for " + outcome.toolName().id());
        }
        String outputJson =
                readToolHandler.executeJson(outcome.toolInputJson(), tenantId.toString());
        String sanitizedOutputJson = sanitizeToolOutput(tenantId, outcome, outputJson);
        UUID toolCallMessageId =
                transactionTemplate.execute(
                        _ ->
                                persistToolCall(
                                        chatId,
                                        tenantId,
                                        outcome.toolCallId(),
                                        outcome.toolName().id(),
                                        outcome.toolInputJson()));
        streamSink.emitDataPersistence(toolCallMessageId, "tool-call-saved");
        streamSink.emitToolOutputAvailable(outcome.toolCallId(), sanitizedOutputJson);
        UUID toolOutputMessageId =
                transactionTemplate.execute(
                        _ ->
                                persistToolOutput(
                                        chatId,
                                        tenantId,
                                        outcome.toolCallId(),
                                        outcome.toolName().id(),
                                        sanitizedOutputJson));
        streamSink.emitDataPersistence(toolOutputMessageId, "tool-output-saved");
    }

    private TurnOutcome awaitOutcome(
            InterceptingSink interceptingSink, TrackingDisposable trackingDisposable) {
        try {
            return interceptingSink.awaitOutcome();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            trackingDisposable.dispose();
            return null;
        } catch (ExecutionException executionException) {
            throw new IllegalStateException("chat turn failed", executionException.getCause());
        }
    }

    private PreparedTurn prepareTurn(UUID tenantId, UUID requestedChatId, String userText) {
        UUID chatId = requestedChatId == null ? UUID.randomUUID() : requestedChatId;
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO chat (id, tenant_id, title, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 0)
                ON CONFLICT (id) DO NOTHING
                """,
                chatId,
                tenantId,
                titleFrom(userText),
                Timestamp.from(now),
                Timestamp.from(now));
        jdbcTemplate.update(
                """
                UPDATE chat
                SET updated_at = ?
                WHERE id = ? AND tenant_id = ?
                """,
                Timestamp.from(now),
                chatId,
                tenantId);
        UUID userMessageId =
                chatMessageRepository.insert(
                        new ChatMessage(
                                new ChatMessageId(UUID.randomUUID()),
                                new ChatId(chatId),
                                tenantId.toString(),
                                ChatRole.USER,
                                ChatMessageParts.v1(
                                        List.of(
                                                new TextPart(
                                                        "user-text-" + UUID.randomUUID(),
                                                        userText))),
                                now));
        return new PreparedTurn(chatId, userMessageId);
    }

    private UUID persistAssistantText(UUID chatId, UUID tenantId, String assistantText) {
        return chatMessageRepository.insert(
                new ChatMessage(
                        new ChatMessageId(UUID.randomUUID()),
                        new ChatId(chatId),
                        tenantId.toString(),
                        ChatRole.ASSISTANT,
                        ChatMessageParts.v1(
                                List.of(
                                        new AssistantTextPart(
                                                "assistant-text-" + UUID.randomUUID(),
                                                assistantText,
                                                Instant.now()))),
                        Instant.now()));
    }

    private UUID persistToolCall(
            UUID chatId, UUID tenantId, String toolCallId, String toolName, String inputJson) {
        ChatToolName chatToolName = ChatToolName.fromId(toolName);
        Map<String, Object> input = parseJsonObject(inputJson);
        UUID chatMessageId =
                chatMessageRepository.insert(
                        new ChatMessage(
                                new ChatMessageId(UUID.randomUUID()),
                                new ChatId(chatId),
                                tenantId.toString(),
                                ChatRole.ASSISTANT,
                                ChatMessageParts.v1(
                                        List.of(
                                                new ToolCallPart(
                                                        "tool-call-" + toolCallId,
                                                        toolCallId,
                                                        toolName,
                                                        "input-available",
                                                        input,
                                                        false))),
                                Instant.now()));
        if (chatToolName.category() != ToolCategory.READ) {
            insertPendingAction(chatId, tenantId, chatMessageId, toolCallId, chatToolName, input);
        }
        return chatMessageId;
    }

    private void insertPendingAction(
            UUID chatId,
            UUID tenantId,
            UUID chatMessageId,
            String toolCallId,
            ChatToolName chatToolName,
            Map<String, Object> input) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                INSERT INTO assistant_pending_action (
                    id,
                    chat_id,
                    tenant_id,
                    chat_message_id,
                    tool_call_id,
                    state,
                    draft_body,
                    expires_at,
                    last_tool_call_id,
                    last_tool_call_state,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, 'input-available', ?, ?, 0)
                """,
                UUID.randomUUID(),
                chatId,
                tenantId,
                chatMessageId,
                toolCallId,
                draftBody(chatToolName, input),
                Timestamp.from(now.plusSeconds(chatProperties.sseTimeoutMinutes() * 60L)),
                toolCallId,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private static String draftBody(ChatToolName chatToolName, Map<String, Object> input) {
        if (chatToolName == ChatToolName.SAVE_DRAFT
                || chatToolName == ChatToolName.SEND_EMAIL
                || chatToolName == ChatToolName.REPLY_EMAIL) {
            return optionalText(input, "body");
        }
        if (chatToolName == ChatToolName.FORWARD_EMAIL) {
            String additionalBody = optionalText(input, "additionalBody");
            return additionalBody == null ? optionalText(input, "body") : additionalBody;
        }
        return null;
    }

    private UUID persistToolOutput(
            UUID chatId, UUID tenantId, String toolCallId, String toolName, String outputJson) {
        return chatMessageRepository.insert(
                new ChatMessage(
                        new ChatMessageId(UUID.randomUUID()),
                        new ChatId(chatId),
                        tenantId.toString(),
                        ChatRole.TOOL,
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolOutputPart(
                                                "tool-output-" + toolCallId,
                                                toolCallId,
                                                toolName,
                                                "output-available",
                                                parseJsonObject(outputJson),
                                                false))),
                        Instant.now()));
    }

    private String sanitizeToolOutput(UUID tenantId, TurnOutcome outcome, String outputJson) {
        ChatMessageParts sanitizedParts =
                toolOutputSanitizer.sanitize(
                        ChatMessageParts.v1(
                                List.of(
                                        new ToolOutputPart(
                                                "tool-output-" + outcome.toolCallId(),
                                                outcome.toolCallId(),
                                                outcome.toolName().id(),
                                                "output-available",
                                                parseJsonObject(outputJson),
                                                false))),
                        tenantId.toString());
        Map<String, Object> sanitizedOutput =
                sanitizedParts.parts().stream()
                        .filter(ToolOutputPart.class::isInstance)
                        .map(ToolOutputPart.class::cast)
                        .findFirst()
                        .map(ToolOutputPart::outputJson)
                        .orElseGet(Map::of);
        return writeJson(sanitizedOutput);
    }

    private Map<String, Object> parseJsonObject(String json) {
        try {
            Object parsedValue =
                    objectMapper.readValue(
                            json == null || json.isBlank() ? "{}" : json, Object.class);
            if (parsedValue instanceof Map<?, ?> parsedMap) {
                Map<String, Object> typedMap = new LinkedHashMap<>();
                parsedMap.forEach((key, value) -> typedMap.put(String.valueOf(key), value));
                return typedMap;
            }
            return Map.of("value", parsedValue);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException("tool JSON must be valid", jacksonException);
        }
    }

    private static String optionalText(Map<String, Object> input, String fieldName) {
        Object value = input == null ? null : input.get(fieldName);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException("tool JSON could not be serialized", jacksonException);
        }
    }

    private List<ChatMessage> budgetHistory(List<ChatMessage> messages, int tokenBudget) {
        if (messages.isEmpty() || tokenBudget <= 0) {
            return messages;
        }
        List<ChatMessage> retainedMessages = new java.util.ArrayList<>();
        int tokenCount = 0;
        for (int messageIndex = messages.size() - 1; messageIndex >= 0; messageIndex--) {
            ChatMessage chatMessage = messages.get(messageIndex);
            int messageTokens = countMessageTokens(chatMessage);
            if (!retainedMessages.isEmpty() && tokenCount + messageTokens > tokenBudget) {
                break;
            }
            retainedMessages.add(chatMessage);
            tokenCount += messageTokens;
        }
        Collections.reverse(retainedMessages);
        return List.copyOf(retainedMessages);
    }

    private int countMessageTokens(ChatMessage chatMessage) {
        int tokenCount = 0;
        for (Part part : chatMessage.parts().parts()) {
            tokenCount += countPartTokens(part);
        }
        return tokenCount;
    }

    private int countPartTokens(Part part) {
        if (part instanceof TextPart textPart) {
            return llmTokenizer.countText(textPart.text());
        }
        if (part instanceof AssistantTextPart assistantTextPart) {
            return llmTokenizer.countText(assistantTextPart.text());
        }
        if (part instanceof ToolCallPart toolCallPart) {
            return llmTokenizer.countSegments(
                    toolCallPart.toolName(), writeJson(toolCallPart.inputJson()));
        }
        if (part instanceof ToolOutputPart toolOutputPart) {
            return llmTokenizer.countSegments(
                    toolOutputPart.toolName(), writeJson(toolOutputPart.outputJson()));
        }
        return 0;
    }

    private static Map<ChatToolName, ChatReadToolHandler> readToolHandlerMap(
            List<ChatReadToolHandler> handlers) {
        EnumMap<ChatToolName, ChatReadToolHandler> handlerMap = new EnumMap<>(ChatToolName.class);
        for (ChatReadToolHandler handler :
                handlers == null ? List.<ChatReadToolHandler>of() : handlers) {
            handlerMap.put(handler.name(), handler);
        }
        return Map.copyOf(handlerMap);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable currentThrowable = throwable;
        while (currentThrowable.getCause() != null) {
            currentThrowable = currentThrowable.getCause();
        }
        return currentThrowable;
    }

    private static String titleFrom(String userText) {
        String normalizedText = userText.replaceAll("\\s+", " ").trim();
        return normalizedText.length() <= TITLE_MAX_LENGTH
                ? normalizedText
                : normalizedText.substring(0, TITLE_MAX_LENGTH);
    }

    private record PreparedTurn(UUID chatId, UUID userMessageId) {}

    private record TurnOutcome(
            String assistantText, ChatToolName toolName, String toolCallId, String toolInputJson) {

        static TurnOutcome assistant(String assistantText) {
            return new TurnOutcome(assistantText == null ? "" : assistantText, null, null, "{}");
        }

        static TurnOutcome tool(String toolName, String toolCallId, String toolInputJson) {
            return new TurnOutcome(
                    "",
                    ChatToolName.fromId(toolName),
                    toolCallId,
                    toolInputJson == null || toolInputJson.isBlank() ? "{}" : toolInputJson);
        }
    }

    private static final class InterceptingSink implements ChatStreamSink {

        private final ChatStreamSink downstreamSink;
        private final CompletableFuture<TurnOutcome> outcomeFuture = new CompletableFuture<>();
        private final StringBuilder assistantText = new StringBuilder();
        private String lastToolName;
        private String lastToolCallId;
        private String lastToolInputJson;

        private InterceptingSink(ChatStreamSink downstreamSink) {
            this.downstreamSink = downstreamSink;
        }

        @Override
        public void emitTextStart(String partId) {
            downstreamSink.emitTextStart(partId);
        }

        @Override
        public void emitTextDelta(String partId, String tokenText) {
            assistantText.append(tokenText == null ? "" : tokenText);
            downstreamSink.emitTextDelta(partId, tokenText);
        }

        @Override
        public void emitTextEnd(String partId) {
            downstreamSink.emitTextEnd(partId);
        }

        @Override
        public void emitToolInputStart(String toolCallId, String toolName) {
            lastToolCallId = toolCallId;
            lastToolName = toolName;
            downstreamSink.emitToolInputStart(toolCallId, toolName);
        }

        @Override
        public void emitToolInputAvailable(String toolCallId, String toolName, String inputJson) {
            lastToolCallId = toolCallId;
            lastToolName = toolName;
            lastToolInputJson = inputJson;
            downstreamSink.emitToolInputAvailable(toolCallId, toolName, inputJson);
        }

        @Override
        public void emitToolOutputAvailable(String toolCallId, String outputJson) {
            downstreamSink.emitToolOutputAvailable(toolCallId, outputJson);
        }

        @Override
        public void emitDataPersistence(UUID chatMessageId, String state) {
            downstreamSink.emitDataPersistence(chatMessageId, state);
        }

        @Override
        public void emitFinish(String reason) {
            outcomeFuture.complete(outcome());
        }

        @Override
        public void emitError(String code, String userFacingMessage) {
            downstreamSink.emitError(code, userFacingMessage);
            outcomeFuture.completeExceptionally(
                    new IllegalStateException(code == null ? "chat_stream_failed" : code));
        }

        @Override
        public void emitHeartbeat() {
            downstreamSink.emitHeartbeat();
        }

        TurnOutcome awaitOutcome() throws InterruptedException, ExecutionException {
            return outcomeFuture.get();
        }

        private TurnOutcome outcome() {
            if (lastToolName != null) {
                return TurnOutcome.tool(lastToolName, lastToolCallId, lastToolInputJson);
            }
            return TurnOutcome.assistant(assistantText.toString());
        }
    }

    private static final class TrackingDisposable implements Disposable {

        private final List<Disposable> subscriptions = new CopyOnWriteArrayList<>();
        private final Scheduler streamScheduler;
        private volatile boolean disposed;

        private TrackingDisposable(Scheduler streamScheduler) {
            this.streamScheduler = streamScheduler;
        }

        void add(Disposable disposable) {
            if (disposable == null) {
                return;
            }
            if (disposed) {
                disposable.dispose();
                return;
            }
            subscriptions.add(disposable);
        }

        @Override
        public void dispose() {
            disposed = true;
            subscriptions.forEach(Disposable::dispose);
            streamScheduler.dispose();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
