package com.zeromail.core.messaging.telegram.webhook;

import com.zeromail.core.chat.usecases.ChatOrchestrator;
import com.zeromail.core.chat.usecases.ChatStreamCommand;
import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import com.zeromail.core.messaging.telegram.domain.TelegramAccountView;
import com.zeromail.core.messaging.telegram.domain.TelegramSendMessageRequest;
import com.zeromail.core.messaging.telegram.gateway.TelegramApiClient;
import com.zeromail.core.messaging.telegram.persistence.TelegramAccountJdbcRepository;
import com.zeromail.core.messaging.telegram.persistence.TelegramUpdateProcessedJdbcRepository;
import com.zeromail.core.messaging.telegram.usecases.InvalidPairingCodeException;
import com.zeromail.core.messaging.telegram.usecases.PairingCodeExpiredException;
import com.zeromail.core.messaging.telegram.usecases.PairingConsumeService;
import com.zeromail.core.messaging.telegram.usecases.TelegramChatStreamSink;
import com.zeromail.core.messaging.telegram.usecases.TelegramConversationResolveCommand;
import com.zeromail.core.messaging.telegram.usecases.TelegramConversationResolver;
import com.zeromail.core.tenant.TenantContext;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelegramUpdateRouter {

    private static final Logger logger = LoggerFactory.getLogger(TelegramUpdateRouter.class);

    private static final String START_WITHOUT_CODE_REPLY =
            "Mo Zero Mail tren web, vao Settings, bam Ket noi Telegram roi gui lenh /start kem ma ket noi.";
    private static final String PAIRING_SUCCESS_REPLY = "Da ket noi Telegram voi Zero Mail.";
    private static final String PAIRING_EXPIRED_REPLY =
            "Ma ket noi da het han. Hay tao ma moi tren web.";
    private static final String PAIRING_INVALID_REPLY = "Ma ket noi khong hop le.";
    private static final String HELP_REPLY =
            "Lenh kha dung:\n/start <code> - ket noi\n/new - tao doan chat moi\n/current - xem trang thai\n/help - tro giup";
    private static final String NOT_CONNECTED_REPLY =
            "Telegram chua duoc ket noi voi Zero Mail. Hay ket noi trong Settings tren web.";
    private static final String NEW_SESSION_REPLY = "Da tao doan chat moi.";
    private static final String CURRENT_REPLY = "Telegram dang ket noi voi Zero Mail.";
    private static final String UNKNOWN_COMMAND_REPLY = "Lenh khong hop le. Go /help de xem lenh.";

    private final TelegramUpdateProcessedJdbcRepository telegramUpdateProcessedJdbcRepository;
    private final TelegramAccountJdbcRepository telegramAccountJdbcRepository;
    private final PairingConsumeService pairingConsumeService;
    private final TelegramConversationResolver telegramConversationResolver;
    private final TelegramApiClient telegramApiClient;
    private final TelegramProperties telegramProperties;
    private final ChatOrchestrator chatOrchestrator;
    private final Clock clock;

    public TelegramUpdateRouter(
            TelegramUpdateProcessedJdbcRepository telegramUpdateProcessedJdbcRepository,
            TelegramAccountJdbcRepository telegramAccountJdbcRepository,
            PairingConsumeService pairingConsumeService,
            TelegramConversationResolver telegramConversationResolver,
            TelegramApiClient telegramApiClient,
            TelegramProperties telegramProperties,
            ChatOrchestrator chatOrchestrator,
            Clock clock) {
        this.telegramUpdateProcessedJdbcRepository = telegramUpdateProcessedJdbcRepository;
        this.telegramAccountJdbcRepository = telegramAccountJdbcRepository;
        this.pairingConsumeService = pairingConsumeService;
        this.telegramConversationResolver = telegramConversationResolver;
        this.telegramApiClient = telegramApiClient;
        this.telegramProperties = telegramProperties;
        this.chatOrchestrator = chatOrchestrator;
        this.clock = clock;
    }

    public void route(TelegramUpdateRequest updateRequest) {
        if (updateRequest == null || updateRequest.message() == null) {
            return;
        }
        if (!telegramUpdateProcessedJdbcRepository.markProcessedIfAbsent(
                updateRequest.updateId())) {
            logger.info(
                    "event=telegram_webhook_duplicate_update updateId={}",
                    updateRequest.updateId());
            return;
        }
        TelegramMessagePayload message = updateRequest.message();
        if (message.chat() == null || !message.chat().privateChat()) {
            logger.info("event=telegram_webhook_non_private_or_missing_chat");
            return;
        }
        if (message.text() == null || message.text().isBlank()) {
            logger.info("event=telegram_webhook_non_text_message chatId={}", message.chat().id());
            return;
        }
        String text = message.text().trim();
        if (text.startsWith("/")) {
            routeCommand(message, text);
            return;
        }
        routeFreeText(message, text);
    }

    private void routeCommand(TelegramMessagePayload message, String text) {
        if (text.equals("/start")) {
            send(message.chat().id(), START_WITHOUT_CODE_REPLY);
            return;
        }
        if (text.startsWith("/start ")) {
            consumePairing(message, text.substring("/start ".length()).trim());
            return;
        }
        if (text.equals("/help")) {
            send(message.chat().id(), HELP_REPLY);
            return;
        }
        if (text.equals("/new")) {
            startNewSession(message);
            return;
        }
        if (text.equals("/current")) {
            current(message);
            return;
        }
        send(message.chat().id(), UNKNOWN_COMMAND_REPLY);
    }

    private void consumePairing(TelegramMessagePayload message, String code) {
        TelegramUserPayload from = message.from();
        long telegramUserId = from == null ? message.chat().id() : from.id();
        String telegramUsername = from == null ? null : from.username();
        String languageCode = from == null ? null : from.languageCode();
        try {
            pairingConsumeService.consume(
                    code, message.chat().id(), telegramUserId, telegramUsername, languageCode);
            send(message.chat().id(), PAIRING_SUCCESS_REPLY);
        } catch (PairingCodeExpiredException expired) {
            send(message.chat().id(), PAIRING_EXPIRED_REPLY);
        } catch (InvalidPairingCodeException invalid) {
            send(message.chat().id(), PAIRING_INVALID_REPLY);
        }
    }

    private void startNewSession(TelegramMessagePayload message) {
        Optional<TelegramAccountView> accountOptional = connectedAccount(message.chat().id());
        if (accountOptional.isEmpty()) {
            send(message.chat().id(), NOT_CONNECTED_REPLY);
            return;
        }
        TelegramAccountView account = accountOptional.get();
        TenantContext.runWith(
                account.tenantId(),
                () -> telegramConversationResolver.startNewSession(commandFor(account)));
        telegramAccountJdbcRepository.touchLastActive(account.tenantId(), clock.instant());
        send(message.chat().id(), NEW_SESSION_REPLY);
    }

    private void current(TelegramMessagePayload message) {
        Optional<TelegramAccountView> accountOptional = connectedAccount(message.chat().id());
        send(
                message.chat().id(),
                accountOptional.isPresent() ? CURRENT_REPLY : NOT_CONNECTED_REPLY);
    }

    private void routeFreeText(TelegramMessagePayload message, String text) {
        Optional<TelegramAccountView> accountOptional = connectedAccount(message.chat().id());
        if (accountOptional.isEmpty()) {
            send(message.chat().id(), NOT_CONNECTED_REPLY);
            return;
        }
        TelegramAccountView account = accountOptional.get();
        TenantContext.runWith(
                account.tenantId(),
                () -> {
                    UUID chatId =
                            telegramConversationResolver
                                    .resolveOrCreateActiveSession(commandFor(account))
                                    .chatId();
                    telegramAccountJdbcRepository.touchLastActive(
                            account.tenantId(), clock.instant());
                    TelegramChatStreamSink streamSink =
                            new TelegramChatStreamSink(telegramApiClient, message.chat().id());
                    chatOrchestrator.stream(
                            new ChatStreamCommand(
                                    account.tenantId().toString(), chatId, text, null),
                            streamSink);
                });
    }

    private Optional<TelegramAccountView> connectedAccount(long telegramChatId) {
        return telegramAccountJdbcRepository
                .findByTelegramChatId(telegramChatId)
                .filter(TelegramAccountView::connected);
    }

    private TelegramConversationResolveCommand commandFor(TelegramAccountView account) {
        return new TelegramConversationResolveCommand(
                account.tenantId(),
                telegramProperties.botAccountId(),
                account.telegramChatId(),
                account.telegramUserId(),
                null);
    }

    private void send(long chatId, String text) {
        telegramApiClient.sendMessage(TelegramSendMessageRequest.plain(chatId, text));
    }
}
