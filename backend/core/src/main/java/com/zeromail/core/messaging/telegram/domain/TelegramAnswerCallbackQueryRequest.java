package com.zeromail.core.messaging.telegram.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelegramAnswerCallbackQueryRequest(
        @JsonProperty("callback_query_id") String callbackQueryId,
        String text,
        @JsonProperty("show_alert") Boolean showAlert) {

    public static TelegramAnswerCallbackQueryRequest notification(
            String callbackQueryId, String text) {
        return new TelegramAnswerCallbackQueryRequest(callbackQueryId, text, Boolean.FALSE);
    }
}
