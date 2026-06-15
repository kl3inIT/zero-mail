package com.zeromail.core.messaging.telegram.usecases;

import java.util.Objects;
import java.util.UUID;

/** Telegram-specific input mapped onto the generic messaging conversation resolver. */
public record TelegramConversationResolveCommand(
        UUID tenantId,
        String botAccountId,
        long telegramChatId,
        long telegramUserId,
        UUID mailAccountId) {

    public TelegramConversationResolveCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        if (botAccountId == null || botAccountId.isBlank()) {
            throw new IllegalArgumentException("botAccountId must not be blank");
        }
        botAccountId = botAccountId.trim();
    }
}
