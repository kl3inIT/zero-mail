package com.zeromail.core.messaging.telegram.usecases;

import com.zeromail.core.messaging.domain.MessagingChannel;
import com.zeromail.core.messaging.usecases.MessagingConversationResolution;
import com.zeromail.core.messaging.usecases.MessagingConversationResolveCommand;
import com.zeromail.core.messaging.usecases.MessagingConversationResolver;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Telegram adapter for stable mobile assistant memory.
 *
 * <p>The stable external conversation key is Telegram's numeric {@code chat.id}; it is never
 * inferred from the newest tenant chat. The returned {@code chatId} is the one the Telegram webhook
 * should pass into {@code ChatOrchestrator.stream(...)}.
 */
@Service
public class TelegramConversationResolver {

    private static final String TELEGRAM_CHAT_TITLE = "Telegram";

    private final MessagingConversationResolver messagingConversationResolver;

    public TelegramConversationResolver(
            MessagingConversationResolver messagingConversationResolver) {
        this.messagingConversationResolver =
                Objects.requireNonNull(
                        messagingConversationResolver, "messagingConversationResolver");
    }

    public MessagingConversationResolution resolveOrCreateActiveSession(
            TelegramConversationResolveCommand command) {
        return messagingConversationResolver.resolveOrCreateActiveSession(
                toGenericCommand(command));
    }

    public MessagingConversationResolution startNewSession(
            TelegramConversationResolveCommand command) {
        return messagingConversationResolver.startNewSession(toGenericCommand(command));
    }

    private static MessagingConversationResolveCommand toGenericCommand(
            TelegramConversationResolveCommand command) {
        Objects.requireNonNull(command, "command");
        return new MessagingConversationResolveCommand(
                command.tenantId(),
                MessagingChannel.TELEGRAM,
                command.botAccountId(),
                Long.toString(command.telegramChatId()),
                Long.toString(command.telegramUserId()),
                command.mailAccountId(),
                TELEGRAM_CHAT_TITLE);
    }
}
