package com.zeromail.core.messaging.telegram.usecases;

import com.zeromail.core.chat.usecases.ChatStreamSink;
import com.zeromail.core.messaging.telegram.domain.TelegramSendMessageRequest;
import com.zeromail.core.messaging.telegram.gateway.TelegramApiClient;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class TelegramChatStreamSink implements ChatStreamSink {

    private static final int TELEGRAM_MESSAGE_LIMIT = 3900;
    private static final long TYPING_REFRESH_SECONDS = 4L;
    private static final String AWAITING_CONFIRMATION_REASON = "awaiting-confirmation";
    private static final String TOOL_CALL_REASON = "tool-call";
    private static final String AWAITING_CONFIRMATION_TEXT =
            "\u0110\u00e3 chu\u1ea9n b\u1ecb xong. M\u1edf Zero Mail tr\u00ean web \u0111\u1ec3 xem tr\u01b0\u1edbc v\u00e0 x\u00e1c nh\u1eadn g\u1eedi.";
    private static final String REVIEW_AND_CONFIRM_TEXT =
            "Ki\u1ec3m tra n\u1ed9i dung b\u00ean d\u01b0\u1edbi r\u1ed3i ch\u1ecdn h\u00e0nh \u0111\u1ed9ng.";
    private static final String CONFIRM_BUTTON = "G\u1eedi qua Gmail";
    private static final String CANCEL_BUTTON = "H\u1ee7y";
    private static final String OPEN_ZERO_MAIL_BUTTON = "M\u1edf Zero Mail";
    private static final String TOOL_CALL_SAVED_STATE = "tool-call-saved";
    private static final String CONFIRM_CALLBACK_PREFIX = "confirm:";
    private static final String CANCEL_CALLBACK_PREFIX = "cancel:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ScheduledExecutorService DEFAULT_TYPING_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "telegram-typing-indicator");
                        thread.setDaemon(true);
                        return thread;
                    });

    private final TelegramApiClient telegramApiClient;
    private final long telegramChatId;
    private final URI confirmationUrl;
    private final ScheduledExecutorService typingScheduler;
    private final StringBuilder responseText = new StringBuilder();
    private final AtomicBoolean typingStarted = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> typingFuture;
    private volatile UUID pendingActionChatMessageId;
    private volatile String pendingToolName;
    private volatile Map<String, Object> pendingToolInput = Map.of();

    public TelegramChatStreamSink(
            TelegramApiClient telegramApiClient, long telegramChatId, URI confirmationUrl) {
        this(telegramApiClient, telegramChatId, confirmationUrl, DEFAULT_TYPING_SCHEDULER);
    }

    TelegramChatStreamSink(
            TelegramApiClient telegramApiClient,
            long telegramChatId,
            URI confirmationUrl,
            ScheduledExecutorService typingScheduler) {
        this.telegramApiClient = telegramApiClient;
        this.telegramChatId = telegramChatId;
        this.confirmationUrl = confirmationUrl;
        this.typingScheduler = typingScheduler;
    }

    public void startTyping() {
        if (!typingStarted.compareAndSet(false, true)) {
            return;
        }
        sendTypingBestEffort();
        typingFuture =
                typingScheduler.scheduleAtFixedRate(
                        this::sendTypingBestEffort,
                        TYPING_REFRESH_SECONDS,
                        TYPING_REFRESH_SECONDS,
                        TimeUnit.SECONDS);
    }

    @Override
    public void emitTextStart(String partId) {
        startTyping();
    }

    @Override
    public void emitTextDelta(String partId, String tokenText) {
        if (tokenText != null) {
            responseText.append(tokenText);
        }
    }

    @Override
    public void emitTextEnd(String partId) {}

    @Override
    public void emitToolInputStart(String toolCallId, String toolName) {}

    @Override
    public void emitToolInputAvailable(String toolCallId, String toolName, String inputJson) {
        pendingToolName = toolName;
        pendingToolInput = parseJsonObject(inputJson);
    }

    @Override
    public void emitToolOutputAvailable(String toolCallId, String outputJson) {}

    @Override
    public void emitDataPersistence(UUID chatMessageId, String state) {
        if (TOOL_CALL_SAVED_STATE.equals(state)) {
            pendingActionChatMessageId = chatMessageId;
        }
    }

    @Override
    public void emitFinish(String reason) {
        if (finished.compareAndSet(false, true)) {
            stopTyping();
            String finalText = responseText.toString().trim();
            if (awaitingConfirmation(reason)) {
                telegramApiClient.sendMessage(confirmationRequest(finalText));
                return;
            }
            if (!finalText.isBlank()) {
                telegramApiClient.sendMessage(
                        TelegramSendMessageRequest.plain(telegramChatId, truncate(finalText)));
            }
        }
    }

    @Override
    public void emitError(String code, String userFacingMessage) {
        if (finished.compareAndSet(false, true)) {
            stopTyping();
            String message =
                    userFacingMessage == null || userFacingMessage.isBlank()
                            ? "Bot \u0111ang g\u1eb7p l\u1ed7i t\u1ea1m th\u1eddi. H\u00e3y th\u1eed l\u1ea1i sau."
                            : telegramErrorMessage(code, userFacingMessage);
            telegramApiClient.sendMessage(
                    TelegramSendMessageRequest.plain(telegramChatId, message));
        }
    }

    @Override
    public void emitHeartbeat() {}

    private void sendTypingBestEffort() {
        try {
            telegramApiClient.sendTyping(telegramChatId);
        } catch (RuntimeException ignored) {
            // Typing is cosmetic. The final Telegram message/error still carries the user-visible
            // result.
        }
    }

    private void stopTyping() {
        ScheduledFuture<?> currentTypingFuture = typingFuture;
        if (currentTypingFuture != null) {
            currentTypingFuture.cancel(false);
        }
    }

    private static boolean awaitingConfirmation(String reason) {
        return AWAITING_CONFIRMATION_REASON.equals(reason) || TOOL_CALL_REASON.equals(reason);
    }

    private TelegramSendMessageRequest confirmationRequest(String finalText) {
        UUID currentPendingActionChatMessageId = pendingActionChatMessageId;
        if (currentPendingActionChatMessageId == null) {
            return TelegramSendMessageRequest.withUrlButton(
                    telegramChatId,
                    confirmationMessage(finalText, pendingToolName, pendingToolInput),
                    OPEN_ZERO_MAIL_BUTTON,
                    confirmationUrl);
        }
        String chatMessageId = currentPendingActionChatMessageId.toString();
        return TelegramSendMessageRequest.withConfirmationButtons(
                telegramChatId,
                confirmationMessage(finalText, pendingToolName, pendingToolInput),
                CONFIRM_BUTTON,
                CONFIRM_CALLBACK_PREFIX + chatMessageId,
                CANCEL_BUTTON,
                CANCEL_CALLBACK_PREFIX + chatMessageId,
                OPEN_ZERO_MAIL_BUTTON,
                confirmationUrl);
    }

    private static String confirmationMessage(
            String finalText, String toolName, Map<String, Object> inputJson) {
        String previewText = previewMessage(toolName, inputJson);
        if (!previewText.isBlank()) {
            String prefix =
                    finalText == null || finalText.isBlank() ? REVIEW_AND_CONFIRM_TEXT : finalText;
            return truncate(prefix + "\n\n" + previewText);
        }
        if (finalText == null || finalText.isBlank()) {
            return AWAITING_CONFIRMATION_TEXT;
        }
        return truncate(finalText + "\n\n" + AWAITING_CONFIRMATION_TEXT);
    }

    private static String previewMessage(String toolName, Map<String, Object> inputJson) {
        if (toolName == null || inputJson == null || inputJson.isEmpty()) {
            return "";
        }
        return switch (toolName) {
            case "sendEmail" -> emailPreview("Email m\u1edbi", inputJson, "body");
            case "replyEmail" -> emailPreview("Tr\u1ea3 l\u1eddi email", inputJson, "body");
            case "forwardEmail" ->
                    emailPreview("Chuy\u1ec3n ti\u1ebfp email", inputJson, "additionalBody");
            case "saveDraft" -> emailPreview("L\u01b0u nh\u00e1p Gmail", inputJson, "body");
            default -> "";
        };
    }

    private static String emailPreview(
            String title, Map<String, Object> inputJson, String bodyField) {
        StringBuilder previewText = new StringBuilder(title);
        appendLine(previewText, "\u0110\u1ebfn", text(inputJson, "to"));
        appendLine(previewText, "Cc", text(inputJson, "cc"));
        appendLine(previewText, "Ch\u1ee7 \u0111\u1ec1", text(inputJson, "subject"));
        String body = text(inputJson, bodyField);
        if (body == null && "additionalBody".equals(bodyField)) {
            body = text(inputJson, "body");
        }
        if (body != null) {
            previewText.append("\nN\u1ed9i dung:\n").append(body);
        }
        return previewText.toString().trim();
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        if (value != null) {
            builder.append("\n").append(label).append(": ").append(value);
        }
    }

    private static String text(Map<String, Object> inputJson, String fieldName) {
        Object value = inputJson.get(fieldName);
        if (value instanceof Map<?, ?> nestedMap) {
            value = nestedMap.get("value");
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).replaceAll("\\R{3,}", "\n\n").trim();
        return text.isBlank() ? null : text;
    }

    private static Map<String, Object> parseJsonObject(String json) {
        try {
            Object parsedValue =
                    OBJECT_MAPPER.readValue(
                            json == null || json.isBlank() ? "{}" : json, Object.class);
            if (parsedValue instanceof Map<?, ?> parsedMap) {
                Map<String, Object> typedMap = new LinkedHashMap<>();
                parsedMap.forEach((key, value) -> typedMap.put(String.valueOf(key), value));
                return typedMap;
            }
            return Map.of();
        } catch (JacksonException jacksonException) {
            return Map.of();
        }
    }

    private static String telegramErrorMessage(String code, String fallbackMessage) {
        if ("chat_too_many_tool_calls".equals(code)) {
            return "Bot c\u1ea7n \u0111\u1ecdc qu\u00e1 nhi\u1ec1u d\u1eef li\u1ec7u cho y\u00eau c\u1ea7u n\u00e0y. "
                    + "H\u00e3y th\u1eed y\u00eau c\u1ea7u h\u1eb9p h\u01a1n ho\u1eb7c th\u1eed l\u1ea1i.";
        }
        if ("The assistant stream failed.".equals(fallbackMessage)) {
            return "Bot \u0111ang g\u1eb7p l\u1ed7i t\u1ea1m th\u1eddi. H\u00e3y th\u1eed l\u1ea1i sau.";
        }
        return fallbackMessage;
    }

    private static String truncate(String text) {
        return text.length() <= TELEGRAM_MESSAGE_LIMIT
                ? text
                : text.substring(0, TELEGRAM_MESSAGE_LIMIT);
    }
}
