package com.zeromail.core.messaging.telegram.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelegramSendMessageRequest(
        @JsonProperty("chat_id") long chatId,
        @JsonProperty("text") String text,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("disable_web_page_preview") Boolean disableWebPagePreview,
        @JsonProperty("reply_markup") TelegramInlineKeyboardMarkup replyMarkup) {

    public static TelegramSendMessageRequest plain(long chatId, String text) {
        return new TelegramSendMessageRequest(chatId, text, null, Boolean.TRUE, null);
    }

    public static TelegramSendMessageRequest withUrlButton(
            long chatId, String text, String buttonText, URI buttonUrl) {
        return new TelegramSendMessageRequest(
                chatId,
                text,
                null,
                Boolean.TRUE,
                TelegramInlineKeyboardMarkup.singleUrlButton(buttonText, buttonUrl));
    }

    public static TelegramSendMessageRequest withConfirmationButtons(
            long chatId,
            String text,
            String confirmButtonText,
            String confirmCallbackData,
            String cancelButtonText,
            String cancelCallbackData,
            String openButtonText,
            URI openButtonUrl) {
        return new TelegramSendMessageRequest(
                chatId,
                text,
                null,
                Boolean.TRUE,
                TelegramInlineKeyboardMarkup.confirmationButtons(
                        confirmButtonText,
                        confirmCallbackData,
                        cancelButtonText,
                        cancelCallbackData,
                        openButtonText,
                        openButtonUrl));
    }

    public record TelegramInlineKeyboardMarkup(
            @JsonProperty("inline_keyboard")
                    List<List<TelegramInlineKeyboardButton>> inlineKeyboard) {

        static TelegramInlineKeyboardMarkup singleUrlButton(String text, URI url) {
            return new TelegramInlineKeyboardMarkup(
                    List.of(List.of(TelegramInlineKeyboardButton.url(text, url))));
        }

        static TelegramInlineKeyboardMarkup confirmationButtons(
                String confirmText,
                String confirmCallbackData,
                String cancelText,
                String cancelCallbackData,
                String openText,
                URI openUrl) {
            return new TelegramInlineKeyboardMarkup(
                    List.of(
                            List.of(
                                    TelegramInlineKeyboardButton.callback(
                                            confirmText, confirmCallbackData),
                                    TelegramInlineKeyboardButton.callback(
                                            cancelText, cancelCallbackData)),
                            List.of(TelegramInlineKeyboardButton.url(openText, openUrl))));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TelegramInlineKeyboardButton(
            String text, String url, @JsonProperty("callback_data") String callbackData) {

        static TelegramInlineKeyboardButton url(String text, URI url) {
            return new TelegramInlineKeyboardButton(text, url.toString(), null);
        }

        static TelegramInlineKeyboardButton callback(String text, String callbackData) {
            return new TelegramInlineKeyboardButton(text, null, callbackData);
        }
    }
}
