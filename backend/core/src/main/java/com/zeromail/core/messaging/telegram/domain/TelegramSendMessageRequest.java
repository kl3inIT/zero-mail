package com.zeromail.core.messaging.telegram.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramSendMessageRequest(
        @JsonProperty("chat_id") long chatId,
        @JsonProperty("text") String text,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("disable_web_page_preview") Boolean disableWebPagePreview) {

    public static TelegramSendMessageRequest plain(long chatId, String text) {
        return new TelegramSendMessageRequest(chatId, text, null, Boolean.TRUE);
    }
}
