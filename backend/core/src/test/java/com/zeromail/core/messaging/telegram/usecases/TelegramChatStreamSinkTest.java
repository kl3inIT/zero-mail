package com.zeromail.core.messaging.telegram.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.messaging.telegram.domain.TelegramSendMessageRequest;
import com.zeromail.core.messaging.telegram.gateway.TelegramApiClient;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class TelegramChatStreamSinkTest {

    private static final long TELEGRAM_CHAT_ID = 5378705410L;
    private static final URI CONFIRMATION_URL =
            URI.create("https://app.zeromail.test/chat?chat=00000000-0000-0000-0000-000000000001");

    private final TelegramApiClient telegramApiClient = mock(TelegramApiClient.class);
    private final ScheduledExecutorService typingScheduler = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> typingFuture = mock(ScheduledFuture.class);

    @Test
    void startTyping_sendsImmediateTypingAndSchedulesRefresh() {
        TelegramChatStreamSink streamSink = streamSink();

        streamSink.startTyping();

        verify(telegramApiClient).sendTyping(TELEGRAM_CHAT_ID);
        verify(typingScheduler)
                .scheduleAtFixedRate(
                        ArgumentMatchers.<Runnable>any(), eq(4L), eq(4L), eq(TimeUnit.SECONDS));
    }

    @Test
    void emitFinish_awaitingConfirmationSendsReviewLinkAndStopsTyping() {
        UUID chatMessageId = UUID.randomUUID();
        TelegramChatStreamSink streamSink = streamSink();
        streamSink.startTyping();
        streamSink.emitDataPersistence(chatMessageId, "tool-call-saved");

        streamSink.emitFinish("awaiting-confirmation");

        ArgumentCaptor<TelegramSendMessageRequest> messageCaptor =
                ArgumentCaptor.forClass(TelegramSendMessageRequest.class);
        verify(typingFuture).cancel(false);
        verify(telegramApiClient).sendMessage(messageCaptor.capture());
        TelegramSendMessageRequest messageRequest = messageCaptor.getValue();
        assertThat(messageRequest.chatId()).isEqualTo(TELEGRAM_CHAT_ID);
        assertThat(messageRequest.text()).contains("Zero Mail");
        assertThat(messageRequest.replyMarkup().inlineKeyboard().get(0).get(0).text())
                .isEqualTo("G\u1eedi qua Gmail");
        assertThat(messageRequest.replyMarkup().inlineKeyboard().get(0).get(0).callbackData())
                .isEqualTo("confirm:" + chatMessageId);
        assertThat(messageRequest.replyMarkup().inlineKeyboard().get(1).get(0).url())
                .isEqualTo(CONFIRMATION_URL.toString());
    }

    @Test
    void emitFinish_awaitingConfirmationIncludesEmailPreview() {
        UUID chatMessageId = UUID.randomUUID();
        TelegramChatStreamSink streamSink = streamSink();
        streamSink.emitToolInputAvailable(
                "call-1",
                "sendEmail",
                "{\"to\":\"founder@example.com\",\"subject\":\"Quick update\",\"body\":\"Hi,\\n\\nThe draft is ready.\"}");
        streamSink.emitDataPersistence(chatMessageId, "tool-call-saved");

        streamSink.emitFinish("awaiting-confirmation");

        ArgumentCaptor<TelegramSendMessageRequest> messageCaptor =
                ArgumentCaptor.forClass(TelegramSendMessageRequest.class);
        verify(telegramApiClient).sendMessage(messageCaptor.capture());
        TelegramSendMessageRequest messageRequest = messageCaptor.getValue();
        assertThat(messageRequest.text())
                .contains("Email m\u1edbi")
                .contains("founder@example.com")
                .contains("Quick update")
                .contains("The draft is ready.");
        assertThat(messageRequest.replyMarkup().inlineKeyboard().get(0).get(0).callbackData())
                .isEqualTo("confirm:" + chatMessageId);
    }

    @Test
    void emitError_tooManyToolCallsUsesTelegramSpecificMessage() {
        TelegramChatStreamSink streamSink = streamSink();

        streamSink.emitError(
                "chat_too_many_tool_calls",
                "The assistant requested too many tool calls. Please try again.");

        ArgumentCaptor<TelegramSendMessageRequest> messageCaptor =
                ArgumentCaptor.forClass(TelegramSendMessageRequest.class);
        verify(telegramApiClient).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().text())
                .contains("Bot c\u1ea7n \u0111\u1ecdc qu\u00e1 nhi\u1ec1u d\u1eef li\u1ec7u");
    }

    private TelegramChatStreamSink streamSink() {
        when(typingScheduler.scheduleAtFixedRate(
                        ArgumentMatchers.<Runnable>any(), eq(4L), eq(4L), eq(TimeUnit.SECONDS)))
                .thenAnswer(_ -> typingFuture);
        return new TelegramChatStreamSink(
                telegramApiClient, TELEGRAM_CHAT_ID, CONFIRMATION_URL, typingScheduler);
    }
}
