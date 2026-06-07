package com.zeromail.core.messaging.telegram.gateway;

public class TelegramNotConfiguredException extends RuntimeException {

    public TelegramNotConfiguredException() {
        super("Telegram integration is not configured");
    }
}
