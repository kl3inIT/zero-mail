package com.zeromail.core.chat.domain.sendaction;

import com.zeromail.core.shared.privacy.Sensitive;

@SuppressWarnings("unused")
public record SendEmailToolArgs(
        String to, String subject, Sensitive<String> body, String replyToMessageId) {

    public SendEmailToolArgs {
        to = requireText(to, "to");
        subject = requireText(subject, "subject");
        body = requireBody(body);
        replyToMessageId = requireText(replyToMessageId, "replyToMessageId");
    }

    public SendEmailToolArgs(String to, String subject, String body, String replyToMessageId) {
        this(to, subject, Sensitive.of(requireText(body, "body")), replyToMessageId);
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static Sensitive<String> requireBody(Sensitive<String> body) {
        if (body == null || body.value().isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        return body;
    }
}
