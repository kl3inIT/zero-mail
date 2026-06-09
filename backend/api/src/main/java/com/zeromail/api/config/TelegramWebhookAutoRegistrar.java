package com.zeromail.api.config;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import com.zeromail.core.messaging.telegram.gateway.TelegramApiClient;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Self-registers the Telegram webhook on API startup when {@code
 * zero-mail.messaging.telegram.webhook-auto-register=true} and a public base URL is configured, so
 * a fresh deploy wires the bot without a manual {@code setWebhook} call.
 *
 * <p>No-op when the channel is not {@link TelegramProperties#configured() configured} or
 * auto-register is off (e.g. local dev driven through a manual tunnel). Runs only in the API
 * process (the one that owns the webhook controller), never the worker. Best-effort: {@link
 * TelegramApiClient} swallows and logs transport/HTTP failures, so a Telegram outage at boot never
 * blocks application startup.
 *
 * <p>Privacy: logs only the webhook host — never the bot token or secret.
 */
@Component
@Profile("!test")
public class TelegramWebhookAutoRegistrar implements CommandLineRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(TelegramWebhookAutoRegistrar.class);

    private final TelegramProperties telegramProperties;
    private final TelegramApiClient telegramApiClient;

    public TelegramWebhookAutoRegistrar(
            TelegramProperties telegramProperties, TelegramApiClient telegramApiClient) {
        this.telegramProperties = telegramProperties;
        this.telegramApiClient = telegramApiClient;
    }

    @Override
    public void run(String... args) {
        if (!telegramProperties.webhookAutoRegisterEnabled()) {
            return;
        }
        URI webhookUrl = telegramProperties.webhookUrl();
        telegramApiClient.registerWebhook(
                webhookUrl.toString(), telegramProperties.webhookSecretToken());
        logger.info("event=telegram_webhook_auto_register_attempted host={}", webhookUrl.getHost());
    }
}
