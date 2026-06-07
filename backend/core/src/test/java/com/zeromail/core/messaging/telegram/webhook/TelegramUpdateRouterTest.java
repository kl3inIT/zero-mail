package com.zeromail.core.messaging.telegram.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.chat.usecases.ChatOrchestrator;
import com.zeromail.core.chat.usecases.ChatStreamCommand;
import com.zeromail.core.chat.usecases.ChatStreamSink;
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

    private final TelegramUpdateRouter router =
            new TelegramUpdateRouter(
                    updateProcessedRepository,
                    accountRepository,
                    pairingConsumeService,
                    conversationResolver,
                    telegramApiClient,
                    telegramProperties(),
                    chatOrchestrator,
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
        when(chatOrchestrator.stream(any(), any())).thenReturn(mock(Disposable.class));

        router.route(update(101L, "hello"));

        ArgumentCaptor<TelegramConversationResolveCommand> resolveCaptor =
                ArgumentCaptor.forClass(TelegramConversationResolveCommand.class);
        ArgumentCaptor<ChatStreamCommand> chatCommandCaptor =
                ArgumentCaptor.forClass(ChatStreamCommand.class);
        verify(conversationResolver).resolveOrCreateActiveSession(resolveCaptor.capture());
        verify(chatOrchestrator).stream(chatCommandCaptor.capture(), any(ChatStreamSink.class));
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

    private static TelegramUpdateRequest update(long updateId, String text) {
        return new TelegramUpdateRequest(
                updateId,
                new TelegramMessagePayload(
                        1L,
                        new TelegramUserPayload(TELEGRAM_USER_ID, "nhuxuanviet", "vi"),
                        new TelegramChatPayload(TELEGRAM_CHAT_ID, "private"),
                        text));
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
                URI.create("https://api.telegram.org"));
    }
}
