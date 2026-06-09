package com.zeromail.core.messaging.telegram.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TelegramSendMessageRequestTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void plain_omitsNullParseMode() throws Exception {
        String json =
                objectMapper.writeValueAsString(
                        TelegramSendMessageRequest.plain(5378705410L, "Đã kết nối thành công."));

        assertThat(json).contains("\"chat_id\":5378705410");
        assertThat(json).contains("\"text\":\"Đã kết nối thành công.\"");
        assertThat(json).contains("\"disable_web_page_preview\":true");
        assertThat(json).doesNotContain("parse_mode");
    }

    @Test
    void withUrlButton_writesTelegramInlineKeyboardMarkup() throws Exception {
        String json =
                objectMapper.writeValueAsString(
                        TelegramSendMessageRequest.withUrlButton(
                                5378705410L,
                                "Review on web",
                                "Open Zero Mail",
                                URI.create("https://app.zeromail.test/chat?chat=abc")));

        assertThat(json).contains("\"reply_markup\"");
        assertThat(json).contains("\"inline_keyboard\"");
        assertThat(json).contains("\"text\":\"Open Zero Mail\"");
        assertThat(json).contains("\"url\":\"https://app.zeromail.test/chat?chat=abc\"");
        assertThat(json).doesNotContain("callback_data");
        assertThat(json).doesNotContain("parse_mode");
    }

    @Test
    void withConfirmationButtons_writesCallbackAndUrlButtons() throws Exception {
        String json =
                objectMapper.writeValueAsString(
                        TelegramSendMessageRequest.withConfirmationButtons(
                                5378705410L,
                                "Review on web",
                                "Send",
                                "confirm:00000000-0000-0000-0000-000000000001",
                                "Cancel",
                                "cancel:00000000-0000-0000-0000-000000000001",
                                "Open",
                                URI.create("https://app.zeromail.test/chat?chat=abc")));

        assertThat(json)
                .contains("\"callback_data\":\"confirm:00000000-0000-0000-0000-000000000001\"");
        assertThat(json)
                .contains("\"callback_data\":\"cancel:00000000-0000-0000-0000-000000000001\"");
        assertThat(json).contains("\"url\":\"https://app.zeromail.test/chat?chat=abc\"");
    }
}
