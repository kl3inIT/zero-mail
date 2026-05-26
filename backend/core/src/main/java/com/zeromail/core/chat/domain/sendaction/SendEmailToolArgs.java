package com.zeromail.core.chat.domain.sendaction;

@SuppressWarnings("unused")
public record SendEmailToolArgs(String to, String subject, String body) {

    public SendEmailToolArgs {
        to = requireText(to, "to");
        subject = requireText(subject, "subject");
        body = requireText(body, "body");
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }
}
