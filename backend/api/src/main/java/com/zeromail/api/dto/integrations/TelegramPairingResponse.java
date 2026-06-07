package com.zeromail.api.dto.integrations;

import java.time.Instant;

public record TelegramPairingResponse(String code, String deeplink, Instant expiresAt) {}
