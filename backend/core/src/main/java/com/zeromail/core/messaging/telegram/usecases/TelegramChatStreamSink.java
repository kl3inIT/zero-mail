package com.zeromail.core.messaging.telegram.usecases;

import com.zeromail.core.chat.usecases.ChatStreamSink;
import com.zeromail.core.messaging.telegram.domain.TelegramSendMessageRequest;
import com.zeromail.core.messaging.telegram.gateway.TelegramApiClient;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelegramChatStreamSink implements ChatStreamSink {

    private static final int TELEGRAM_MESSAGE_LIMIT = 3900;
    private static final long TYPING_REFRESH_SECONDS = 4L;
    private static final String AWAITING_CONFIRMATION_REASON = "awaiting-confirmation";
    private static final String TOOL_CALL_REASON = "tool-call";
    private static final String AWAITING_CONFIRMATION_TEXT =
            "\u0110\u00e3 chu\u1ea9n b\u1ecb xong. M\u1edf Zero Mail tr\u00ean web \u0111\u1ec3 xem tr\u01b0\u1edbc v\u00e0 x\u00e1c nh\u1eadn g\u1eedi.";
    private static final String CONFIRM_BUTTON = "G\u1eedi qua Gmail";
    private static final String CANCEL_BUTTON = "H\u1ee7y";
    private static final String OPEN_ZERO_MAIL_BUTTON = "M\u1edf Zero Mail";
    private static final String TOOL_CALL_SAVED_STATE = "tool-call-saved";
    private static final String CONFIRM_CALLBACK_PREFIX = "confirm:";
    private static final String CANCEL_CALLBACK_PREFIX = "cancel:";
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
    public void emitToolInputAvailable(String toolCallId, String toolName, String inputJson) {}

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
                            ? "Bot dang gap loi tam thoi. Hay thu lai sau."
                            : userFacingMessage;
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

    private static String confirmationMessage(String finalText) {
        if (finalText.isBlank()) {
            return AWAITING_CONFIRMATION_TEXT;
        }
        return truncate(finalText + "\n\n" + AWAITING_CONFIRMATION_TEXT);
    }

    private TelegramSendMessageRequest confirmationRequest(String finalText) {
        UUID currentPendingActionChatMessageId = pendingActionChatMessageId;
        if (currentPendingActionChatMessageId == null) {
            return TelegramSendMessageRequest.withUrlButton(
                    telegramChatId,
                    confirmationMessage(finalText),
                    OPEN_ZERO_MAIL_BUTTON,
                    confirmationUrl);
        }
        String chatMessageId = currentPendingActionChatMessageId.toString();
        return TelegramSendMessageRequest.withConfirmationButtons(
                telegramChatId,
                confirmationMessage(finalText),
                CONFIRM_BUTTON,
                CONFIRM_CALLBACK_PREFIX + chatMessageId,
                CANCEL_BUTTON,
                CANCEL_CALLBACK_PREFIX + chatMessageId,
                OPEN_ZERO_MAIL_BUTTON,
                confirmationUrl);
    }

    private static String truncate(String text) {
        return text.length() <= TELEGRAM_MESSAGE_LIMIT
                ? text
                : text.substring(0, TELEGRAM_MESSAGE_LIMIT);
    }
}
