package com.zeromail.core.messaging.telegram.webhook;

import com.zeromail.core.chat.exception.ConfirmationLeaseConflictException;
import com.zeromail.core.chat.exception.GmailSendFailedException;
import com.zeromail.core.chat.exception.PendingActionNotFoundException;
import com.zeromail.core.chat.exception.StaleToolCallException;
import com.zeromail.core.chat.exception.VipAcknowledgmentMissingException;
import com.zeromail.core.chat.usecases.ChatOrchestrator;
import com.zeromail.core.chat.usecases.ChatStreamCommand;
import com.zeromail.core.chat.usecases.ConfirmActionService;
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
import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TelegramUpdateRouter {

    private static final Logger logger = LoggerFactory.getLogger(TelegramUpdateRouter.class);

    private static final String START_WITHOUT_CODE_REPLY =
            "Mở Zero Mail trên web, vào Cài đặt, bấm Kết nối Telegram rồi gửi lệnh /start kèm mã kết nối.";

    private static final String PAIRING_SUCCESS_REPLY = "Đã kết nối thành công.";

    private static final String PAIRING_EXPIRED_REPLY =
            "Mã kết nối đã hết hạn. Hãy tạo mã mới trên web.";

    private static final String PAIRING_INVALID_REPLY = "Mã kết nối không hợp lệ.";

    private static final String HELP_REPLY =
            "Các lệnh khả dụng:\n"
                    + "/start <code> - kết nối\n"
                    + "/new - tạo đoạn chat mới\n"
                    + "/current - xem trạng thái\n"
                    + "/help - trợ giúp";

    private static final String NOT_CONNECTED_REPLY =
            "Telegram chưa được kết nối với Zero Mail. Hãy kết nối trong Cài đặt trên web.";

    private static final String NEW_SESSION_REPLY = "Đã tạo đoạn chat mới.";

    private static final String CURRENT_REPLY = "Telegram đang kết nối với Zero Mail.";

    private static final String UNKNOWN_COMMAND_REPLY =
            "Lệnh không hợp lệ. Gõ /help để xem các lệnh.";

    private static final String CONFIRM_CALLBACK_PREFIX = "confirm:";
    private static final String CANCEL_CALLBACK_PREFIX = "cancel:";

    private static final String CALLBACK_PROCESSING_REPLY = "Đang xử lý...";

    private static final String CALLBACK_SENT_REPLY = "Đã gửi/xác nhận xong.";

    private static final String CALLBACK_CANCELED_REPLY = "Đã hủy hành động này.";

    private static final String CALLBACK_NOT_FOUND_REPLY = "Hành động này không còn khả dụng.";

    private static final String CALLBACK_CONFLICT_REPLY =
            "Hành động này đang được xử lý hoặc đã thay đổi.";

    private static final String CALLBACK_VIP_REPLY =
            "Người nhận nằm trong safety-net. Hãy mở web để xác nhận rõ ràng trước khi gửi.";

    private static final String CALLBACK_FAILED_REPLY = "Gửi thất bại.";

    private final TelegramUpdateProcessedJdbcRepository telegramUpdateProcessedJdbcRepository;
    private final TelegramAccountJdbcRepository telegramAccountJdbcRepository;
    private final PairingConsumeService pairingConsumeService;
    private final TelegramConversationResolver telegramConversationResolver;
    private final TelegramApiClient telegramApiClient;
    private final TelegramProperties telegramProperties;
    private final ChatOrchestrator chatOrchestrator;
    private final ConfirmActionService confirmActionService;
    private final Clock clock;

    public TelegramUpdateRouter(
            TelegramUpdateProcessedJdbcRepository telegramUpdateProcessedJdbcRepository,
            TelegramAccountJdbcRepository telegramAccountJdbcRepository,
            PairingConsumeService pairingConsumeService,
            TelegramConversationResolver telegramConversationResolver,
            TelegramApiClient telegramApiClient,
            TelegramProperties telegramProperties,
            ChatOrchestrator chatOrchestrator,
            ConfirmActionService confirmActionService,
            Clock clock) {
        this.telegramUpdateProcessedJdbcRepository = telegramUpdateProcessedJdbcRepository;
        this.telegramAccountJdbcRepository = telegramAccountJdbcRepository;
        this.pairingConsumeService = pairingConsumeService;
        this.telegramConversationResolver = telegramConversationResolver;
        this.telegramApiClient = telegramApiClient;
        this.telegramProperties = telegramProperties;
        this.chatOrchestrator = chatOrchestrator;
        this.confirmActionService = confirmActionService;
        this.clock = clock;
    }

    public void route(TelegramUpdateRequest updateRequest) {
        if (updateRequest == null) {
            return;
        }
        if (!telegramUpdateProcessedJdbcRepository.markProcessedIfAbsent(
                updateRequest.updateId())) {
            logger.info(
                    "event=telegram_webhook_duplicate_update updateId={}",
                    updateRequest.updateId());
            return;
        }
        if (updateRequest.callbackQuery() != null) {
            routeCallback(updateRequest.callbackQuery());
            return;
        }
        if (updateRequest.message() == null) {
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

    private void routeCallback(TelegramCallbackQueryPayload callbackQuery) {
        if (callbackQuery.id() == null || callbackQuery.message() == null) {
            return;
        }
        TelegramMessagePayload message = callbackQuery.message();
        if (message.chat() == null || !message.chat().privateChat()) {
            answerCallback(callbackQuery.id(), CALLBACK_NOT_FOUND_REPLY);
            return;
        }
        Optional<TelegramAccountView> accountOptional = connectedAccount(message.chat().id());
        if (accountOptional.isEmpty()) {
            answerCallback(callbackQuery.id(), NOT_CONNECTED_REPLY);
            return;
        }
        CallbackAction callbackAction = parseCallbackAction(callbackQuery.data());
        if (callbackAction == null) {
            answerCallback(callbackQuery.id(), CALLBACK_NOT_FOUND_REPLY);
            return;
        }
        TelegramAccountView account = accountOptional.get();
        answerCallback(callbackQuery.id(), CALLBACK_PROCESSING_REPLY);
        TenantContext.runWith(
                account.tenantId(),
                () -> {
                    try {
                        ConfirmActionService.ConfirmActionResult result =
                                callbackAction.confirm()
                                        ? confirmActionService.confirmByChatMessageId(
                                                callbackAction.chatMessageId(),
                                                false,
                                                java.util.Map.of())
                                        : confirmActionService.cancelByChatMessageId(
                                                callbackAction.chatMessageId());
                        telegramAccountJdbcRepository.touchLastActive(
                                account.tenantId(), clock.instant());
                        send(
                                message.chat().id(),
                                callbackAction.confirm()
                                        ? successText(result)
                                        : CALLBACK_CANCELED_REPLY);
                    } catch (PendingActionNotFoundException pendingActionNotFoundException) {
                        send(message.chat().id(), CALLBACK_NOT_FOUND_REPLY);
                    } catch (ConfirmationLeaseConflictException | StaleToolCallException conflict) {
                        send(message.chat().id(), CALLBACK_CONFLICT_REPLY);
                    } catch (VipAcknowledgmentMissingException vipAcknowledgmentMissingException) {
                        send(message.chat().id(), CALLBACK_VIP_REPLY);
                    } catch (GmailSendFailedException gmailSendFailedException) {
                        send(message.chat().id(), CALLBACK_FAILED_REPLY);
                    } catch (RuntimeException runtimeException) {
                        logger.warn(
                                "event=telegram_callback_failed tenantId={} failure={}",
                                account.tenantId(),
                                runtimeException.getClass().getSimpleName());
                        send(message.chat().id(), CALLBACK_FAILED_REPLY);
                    }
                });
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
                            new TelegramChatStreamSink(
                                    telegramApiClient,
                                    message.chat().id(),
                                    confirmationUrl(chatId));
                    streamSink.startTyping();
                    try {
                        chatOrchestrator.stream(
                                new ChatStreamCommand(
                                        account.tenantId().toString(), chatId, text, null),
                                streamSink);
                    } catch (RuntimeException runtimeException) {
                        logger.warn(
                                "event=telegram_chat_stream_failed tenantId={} failure={}",
                                account.tenantId(),
                                runtimeException.getClass().getSimpleName());
                        streamSink.emitError(
                                "telegram_chat_stream_failed", "The assistant stream failed.");
                    }
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

    private URI confirmationUrl(UUID chatId) {
        return UriComponentsBuilder.fromUri(telegramProperties.webBaseUrl())
                .path("/chat")
                .queryParam("chat", chatId)
                .build()
                .toUri();
    }

    private void answerCallback(String callbackQueryId, String text) {
        telegramApiClient.answerCallbackQuery(callbackQueryId, text);
    }

    private static CallbackAction parseCallbackAction(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        if (data.startsWith(CONFIRM_CALLBACK_PREFIX)) {
            return parseCallbackAction(data.substring(CONFIRM_CALLBACK_PREFIX.length()), true);
        }
        if (data.startsWith(CANCEL_CALLBACK_PREFIX)) {
            return parseCallbackAction(data.substring(CANCEL_CALLBACK_PREFIX.length()), false);
        }
        return null;
    }

    private static CallbackAction parseCallbackAction(String rawChatMessageId, boolean confirm) {
        try {
            return new CallbackAction(UUID.fromString(rawChatMessageId), confirm);
        } catch (RuntimeException invalidUuid) {
            return null;
        }
    }

    private static String successText(ConfirmActionService.ConfirmActionResult result) {
        return "CONFIRMED".equals(result.state()) ? CALLBACK_SENT_REPLY : CALLBACK_SENT_REPLY;
    }

    private void send(long chatId, String text) {
        telegramApiClient.sendMessage(TelegramSendMessageRequest.plain(chatId, text));
    }

    private record CallbackAction(UUID chatMessageId, boolean confirm) {}
}
