package com.zeromail.api.config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import com.zeromail.core.messaging.telegram.gateway.TelegramApiClient;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TelegramWebhookAutoRegistrarTest {

    private final TelegramApiClient telegramApiClient = Mockito.mock(TelegramApiClient.class);

    @Test
    void registers_webhook_when_auto_register_enabled_and_configured() {
        TelegramProperties properties = properties(true, URI.create("https://zeromail.vn"));

        new TelegramWebhookAutoRegistrar(properties, telegramApiClient).run();

        // Trailing-slash-free base + the fixed webhook path; secret token forwarded verbatim.
        verify(telegramApiClient)
                .registerWebhook(
                        "https://zeromail.vn/api/integrations/telegram/webhook", "webhook-secret");
    }

    @Test
    void strips_trailing_slash_from_public_base_url() {
        TelegramProperties properties = properties(true, URI.create("https://zeromail.vn/"));

        new TelegramWebhookAutoRegistrar(properties, telegramApiClient).run();

        verify(telegramApiClient)
                .registerWebhook(
                        "https://zeromail.vn/api/integrations/telegram/webhook", "webhook-secret");
    }

    @Test
    void does_nothing_when_auto_register_disabled() {
        TelegramProperties properties = properties(false, URI.create("https://zeromail.vn"));

        new TelegramWebhookAutoRegistrar(properties, telegramApiClient).run();

        verifyNoInteractions(telegramApiClient);
    }

    @Test
    void does_nothing_when_public_base_url_missing() {
        TelegramProperties properties = properties(true, null);

        new TelegramWebhookAutoRegistrar(properties, telegramApiClient).run();

        verify(telegramApiClient, never())
                .registerWebhook(Mockito.anyString(), Mockito.anyString());
    }

    private static TelegramProperties properties(boolean autoRegister, URI publicBaseUrl) {
        return new TelegramProperties(
                true,
                "telegram-token",
                "ZeroMailBot",
                "telegram-bot",
                "webhook-secret",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                URI.create("https://api.telegram.org"),
                URI.create("https://app.zeromail.test"),
                autoRegister,
                publicBaseUrl);
    }
}
