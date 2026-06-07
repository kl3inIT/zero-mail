package com.zeromail.core.messaging.telegram.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramSendChatActionRequest(
        @JsonProperty("chat_id") long chatId, @JsonProperty("action") String action) {

    public static TelegramSendChatActionRequest typing(long chatId) {
        return new TelegramSendChatActionRequest(chatId, "typing");
    }
}
