package com.zeromail.core.messaging.telegram.usecases;

import com.zeromail.core.chat.usecases.ChatStreamSink;
import com.zeromail.core.messaging.telegram.domain.TelegramSendMessageRequest;
import com.zeromail.core.messaging.telegram.gateway.TelegramApiClient;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TelegramChatStreamSink implements ChatStreamSink {

    private static final int TELEGRAM_MESSAGE_LIMIT = 3900;

    private final TelegramApiClient telegramApiClient;
    private final long telegramChatId;
    private final StringBuilder responseText = new StringBuilder();
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public TelegramChatStreamSink(TelegramApiClient telegramApiClient, long telegramChatId) {
        this.telegramApiClient = telegramApiClient;
        this.telegramChatId = telegramChatId;
    }

    @Override
    public void emitTextStart(String partId) {
        telegramApiClient.sendTyping(telegramChatId);
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
    public void emitDataPersistence(UUID chatMessageId, String state) {}

    @Override
    public void emitFinish(String reason) {
        if (finished.compareAndSet(false, true)) {
            String finalText = responseText.toString().trim();
            if (!finalText.isBlank()) {
                telegramApiClient.sendMessage(
                        TelegramSendMessageRequest.plain(telegramChatId, truncate(finalText)));
            }
        }
    }

    @Override
    public void emitError(String code, String userFacingMessage) {
        if (finished.compareAndSet(false, true)) {
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

    private static String truncate(String text) {
        return text.length() <= TELEGRAM_MESSAGE_LIMIT
                ? text
                : text.substring(0, TELEGRAM_MESSAGE_LIMIT);
    }
}
