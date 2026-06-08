package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.messaging.telegram.config.TelegramProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TelegramWebhookSecretFilterTest {

    private static final String HEADER_NAME = "X-Telegram-Bot-Api-Secret-Token";

    @Test
    void doFilter_returnsServiceUnavailableWhenTelegramIsNotConfigured() throws Exception {
        TelegramWebhookSecretFilter filter =
                new TelegramWebhookSecretFilter(unconfiguredProperties());
        MockHttpServletRequest request = webhookRequest();
        request.addHeader(HEADER_NAME, "webhook-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(filterChain.getRequest()).isNull();
    }

    @Test
    void doFilter_returnsUnauthorizedWhenSecretHeaderIsMissingOrWrong() throws Exception {
        TelegramWebhookSecretFilter filter =
                new TelegramWebhookSecretFilter(configuredProperties());
        MockHttpServletRequest request = webhookRequest();
        request.addHeader(HEADER_NAME, "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(filterChain.getRequest()).isNull();
    }

    @Test
    void doFilter_continuesWhenSecretHeaderMatches() throws Exception {
        TelegramWebhookSecretFilter filter =
                new TelegramWebhookSecretFilter(configuredProperties());
        MockHttpServletRequest request = webhookRequest();
        request.addHeader(HEADER_NAME, "webhook-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletRequest webhookRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/integrations/telegram/webhook");
        request.setContentType("application/json");
        return request;
    }

    private static TelegramProperties configuredProperties() {
        return new TelegramProperties(
                true,
                "telegram-token",
                "ZeroMailBot",
                "telegram-bot",
                "webhook-secret",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                URI.create("https://api.telegram.org"),
                URI.create("https://app.zeromail.test"),
                false,
                null);
    }

    private static TelegramProperties unconfiguredProperties() {
        return new TelegramProperties(
                false,
                "telegram-token",
                "ZeroMailBot",
                "telegram-bot",
                "webhook-secret",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                URI.create("https://api.telegram.org"),
                URI.create("https://app.zeromail.test"),
                false,
                null);
    }
}
