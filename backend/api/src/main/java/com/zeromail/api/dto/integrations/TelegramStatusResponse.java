package com.zeromail.api.dto.integrations;

import java.time.Instant;

public record TelegramStatusResponse(
        boolean configured,
        boolean connected,
        String telegramUsername,
        Instant linkedAt,
        Instant lastActiveAt) {}
