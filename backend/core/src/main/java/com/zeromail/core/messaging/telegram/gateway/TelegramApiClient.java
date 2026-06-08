package com.zeromail.core.messaging.telegram.gateway;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import com.zeromail.core.messaging.telegram.domain.TelegramAnswerCallbackQueryRequest;
import com.zeromail.core.messaging.telegram.domain.TelegramApiResponse;
import com.zeromail.core.messaging.telegram.domain.TelegramSendChatActionRequest;
import com.zeromail.core.messaging.telegram.domain.TelegramSendMessageRequest;
import com.zeromail.core.messaging.telegram.domain.TelegramSetWebhookRequest;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TelegramApiClient {

    private static final Logger logger = LoggerFactory.getLogger(TelegramApiClient.class);

    private final TelegramProperties telegramProperties;
    private final RestClient.Builder restClientBuilder;

    public TelegramApiClient(
            TelegramProperties telegramProperties, RestClient.Builder restClientBuilder) {
        this.telegramProperties = Objects.requireNonNull(telegramProperties, "telegramProperties");
        this.restClientBuilder = Objects.requireNonNull(restClientBuilder, "restClientBuilder");
    }

    public void sendMessage(TelegramSendMessageRequest request) {
        post("sendMessage", request);
    }

    public void sendTyping(long chatId) {
        post("sendChatAction", TelegramSendChatActionRequest.typing(chatId));
    }

    public void registerWebhook(String webhookUrl, String secretToken) {
        post("setWebhook", TelegramSetWebhookRequest.of(webhookUrl, secretToken));
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        post(
                "answerCallbackQuery",
                TelegramAnswerCallbackQueryRequest.notification(callbackQueryId, text));
    }

    private void post(String method, Object request) {
        if (!telegramProperties.configured()) {
            throw new TelegramNotConfiguredException();
        }
        try {
            TelegramApiResponse response =
                    restClientBuilder
                            .clone()
                            .baseUrl(telegramProperties.botApiBaseUrl())
                            .build()
                            .post()
                            .uri("/" + method)
                            .body(request)
                            .retrieve()
                            .body(TelegramApiResponse.class);
            if (response != null && !response.ok()) {
                logger.warn(
                        "event=telegram_api_rejected method={} descriptionPresent={}",
                        method,
                        response.description() != null && !response.description().isBlank());
            }
        } catch (RestClientResponseException responseException) {
            logger.warn(
                    "event=telegram_api_http_failed method={} status={}",
                    method,
                    responseException.getStatusCode().value());
        } catch (RestClientException restClientException) {
            logger.warn(
                    "event=telegram_api_transport_failed method={} failure={}",
                    method,
                    restClientException.getClass().getSimpleName());
        }
    }
}
