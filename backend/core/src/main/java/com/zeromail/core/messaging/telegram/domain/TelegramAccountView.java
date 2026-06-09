package com.zeromail.core.messaging.telegram.domain;

import java.time.Instant;
import java.util.UUID;

public record TelegramAccountView(
        UUID tenantId,
        long telegramChatId,
        long telegramUserId,
        String telegramUsername,
        String languageCode,
        TelegramAccountStatus status,
        Instant linkedAt,
        Instant lastActiveAt) {

    public boolean connected() {
        return status == TelegramAccountStatus.CONNECTED;
    }
}
