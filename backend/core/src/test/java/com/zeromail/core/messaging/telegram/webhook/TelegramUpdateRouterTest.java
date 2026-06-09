package com.zeromail.core.messaging.telegram.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.chat.usecases.ChatOrchestrator;
import com.zeromail.core.chat.usecases.ChatStreamCommand;
import com.zeromail.core.chat.usecases.ChatStreamSink;
import com.zeromail.core.chat.usecases.ConfirmActionService;
import com.zeromail.core.messaging.domain.MessagingChannel;
import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import com.zeromail.core.messaging.telegram.domain.TelegramAccountStatus;
import com.zeromail.core.messaging.telegram.domain.TelegramAccountView;
import com.zeromail.core.messaging.telegram.domain.TelegramSendMessageRequest;
import com.zeromail.core.messaging.telegram.gateway.TelegramApiClient;
import com.zeromail.core.messaging.telegram.persistence.TelegramAccountJdbcRepository;
import com.zeromail.core.messaging.telegram.persistence.TelegramUpdateProcessedJdbcRepository;
import com.zeromail.core.messaging.telegram.usecases.PairingConsumeService;
import com.zeromail.core.messaging.telegram.usecases.TelegramConversationResolveCommand;
import com.zeromail.core.messaging.telegram.usecases.TelegramConversationResolver;
import com.zeromail.core.messaging.usecases.MessagingConversationResolution;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.Disposable;

class TelegramUpdateRouterTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final long TELEGRAM_CHAT_ID = 5378705410L;
    private static final long TELEGRAM_USER_ID = 5378705410L;
    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");

    private final TelegramUpdateProcessedJdbcRepository updateProcessedRepository =
            mock(TelegramUpdateProcessedJdbcRepository.class);
    private final TelegramAccountJdbcRepository accountRepository =
            mock(TelegramAccountJdbcRepository.class);
    private final PairingConsumeService pairingConsumeService = mock(PairingConsumeService.class);
    private final TelegramConversationResolver conversationResolver =
            mock(TelegramConversationResolver.class);
    private final TelegramApiClient telegramApiClient = mock(TelegramApiClient.class);
    private final ChatOrchestrator chatOrchestrator = mock(ChatOrchestrator.class);
    private final ConfirmActionService confirmActionService = mock(ConfirmActionService.class);

    private final TelegramUpdateRouter router =
            new TelegramUpdateRouter(
                    updateProcessedRepository,
                    accountRepository,
                    pairingConsumeService,
                    conversationResolver,
                    telegramApiClient,
                    telegramProperties(),
                    chatOrchestrator,
                    confirmActionService,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void newCommand_startsNewSessionWithoutCallingChatOrchestrator() {
        when(updateProcessedRepository.markProcessedIfAbsent(100L)).thenReturn(true);
        when(accountRepository.findByTelegramChatId(TELEGRAM_CHAT_ID))
                .thenReturn(Optional.of(account()));
        when(conversationResolver.startNewSession(any())).thenReturn(resolution(UUID.randomUUID()));

        router.route(update(100L, "/new"));

        verify(conversationResolver).startNewSession(any(TelegramConversationResolveCommand.class));
        verify(chatOrchestrator, never()).stream(any(), any());
        verify(telegramApiClient).sendMessage(any(TelegramSendMessageRequest.class));
    }

    @Test
    void freeText_resolvesStableTelegramConversationBeforeStreaming() {
        UUID chatId = UUID.randomUUID();
        when(updateProcessedRepository.markProcessedIfAbsent(101L)).thenReturn(true);
        when(accountRepository.findByTelegramChatId(TELEGRAM_CHAT_ID))
                .thenReturn(Optional.of(account()));
        when(conversationResolver.resolveOrCreateActiveSession(any()))
                .thenReturn(resolution(chatId));
        Disposable streamDisposable = mock(Disposable.class);
        when(chatOrchestrator.stream(any(), any()))
                .thenAnswer(
                        invocation -> {
                            ChatStreamSink streamSink = invocation.getArgument(1);
                            streamSink.emitFinish("complete");
                            return streamDisposable;
                        });

        router.route(update(101L, "hello"));

        ArgumentCaptor<TelegramConversationResolveCommand> resolveCaptor =
                ArgumentCaptor.forClass(TelegramConversationResolveCommand.class);
        ArgumentCaptor<ChatStreamCommand> chatCommandCaptor =
                ArgumentCaptor.forClass(ChatStreamCommand.class);
        verify(conversationResolver).resolveOrCreateActiveSession(resolveCaptor.capture());
        verify(chatOrchestrator).stream(chatCommandCaptor.capture(), any(ChatStreamSink.class));
        verify(telegramApiClient).sendTyping(TELEGRAM_CHAT_ID);
        assertThat(resolveCaptor.getValue().telegramChatId()).isEqualTo(TELEGRAM_CHAT_ID);
        assertThat(chatCommandCaptor.getValue().chatId()).isEqualTo(chatId);
    }

    @Test
    void duplicateUpdate_isIgnored() {
        when(updateProcessedRepository.markProcessedIfAbsent(102L)).thenReturn(false);

        router.route(update(102L, "hello"));

        verify(accountRepository, never()).findByTelegramChatId(anyLong());
        verify(chatOrchestrator, never()).stream(any(), any());
    }

    @Test
    void startWithPairingCode_sendsSuccessReplyAfterPairing() {
        when(updateProcessedRepository.markProcessedIfAbsent(103L)).thenReturn(true);

        router.route(update(103L, "/start pairing-code"));

        verify(pairingConsumeService)
                .consume("pairing-code", TELEGRAM_CHAT_ID, TELEGRAM_USER_ID, "nhuxuanviet", "vi");
        ArgumentCaptor<TelegramSendMessageRequest> replyCaptor =
                ArgumentCaptor.forClass(TelegramSendMessageRequest.class);
        verify(telegramApiClient).sendMessage(replyCaptor.capture());
        assertThat(replyCaptor.getValue().chatId()).isEqualTo(TELEGRAM_CHAT_ID);
        assertThat(replyCaptor.getValue().text()).isEqualTo("Đã kết nối thành công.");
    }

    @Test
    void callbackConfirm_confirmsPendingActionByChatMessageId() {
        UUID chatMessageId = UUID.randomUUID();
        when(updateProcessedRepository.markProcessedIfAbsent(104L)).thenReturn(true);
        when(accountRepository.findByTelegramChatId(TELEGRAM_CHAT_ID))
                .thenReturn(Optional.of(account()));
        when(confirmActionService.confirmByChatMessageId(eq(chatMessageId), eq(false), any()))
                .thenReturn(
                        new ConfirmActionService.ConfirmActionResult(
                                "CONFIRMED", java.util.Map.of()));

        router.route(callbackUpdate(104L, "callback-1", "confirm:" + chatMessageId));

        verify(telegramApiClient).answerCallbackQuery("callback-1", "Đang xử lý...");
        verify(confirmActionService).confirmByChatMessageId(eq(chatMessageId), eq(false), any());
        ArgumentCaptor<TelegramSendMessageRequest> replyCaptor =
                ArgumentCaptor.forClass(TelegramSendMessageRequest.class);
        verify(telegramApiClient).sendMessage(replyCaptor.capture());
        assertThat(replyCaptor.getValue().text()).contains("Đã gửi");
    }

    private static TelegramUpdateRequest update(long updateId, String text) {
        return new TelegramUpdateRequest(
                updateId,
                new TelegramMessagePayload(
                        1L,
                        new TelegramUserPayload(TELEGRAM_USER_ID, "nhuxuanviet", "vi"),
                        new TelegramChatPayload(TELEGRAM_CHAT_ID, "private"),
                        text),
                null);
    }

    private static TelegramUpdateRequest callbackUpdate(
            long updateId, String callbackQueryId, String data) {
        return new TelegramUpdateRequest(
                updateId,
                null,
                new TelegramCallbackQueryPayload(
                        callbackQueryId,
                        new TelegramUserPayload(TELEGRAM_USER_ID, "nhuxuanviet", "vi"),
                        new TelegramMessagePayload(
                                10L,
                                new TelegramUserPayload(1L, "ZeroMailBot", "en"),
                                new TelegramChatPayload(TELEGRAM_CHAT_ID, "private"),
                                null),
                        data));
    }

    private static TelegramAccountView account() {
        return new TelegramAccountView(
                TENANT_ID,
                TELEGRAM_CHAT_ID,
                TELEGRAM_USER_ID,
                "nhuxuanviet",
                "vi",
                TelegramAccountStatus.CONNECTED,
                NOW,
                NOW);
    }

    private static MessagingConversationResolution resolution(UUID chatId) {
        return new MessagingConversationResolution(
                TENANT_ID,
                MessagingChannel.TELEGRAM,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                chatId,
                null,
                false,
                false);
    }

    private static TelegramProperties telegramProperties() {
        return new TelegramProperties(
                true,
                "token",
                "ZeroMailBot",
                "telegram-bot",
                "webhook-secret",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                URI.create("https://api.telegram.org"),
                URI.create("https://app.zeromail.test"),
                false,
                null);
    }
}
