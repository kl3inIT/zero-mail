package com.zeromail.core.chat.domain.sendaction;

@SuppressWarnings("unused")
public record SendEmailToolArgs(String to, String subject, String body) {

    public SendEmailToolArgs {
        to = requireText(to, "to");
        // Subject is intentionally blank-tolerant: Gmail renders an empty subject as
        // "(no subject)". The LLM is instructed to always compose one, but a user who
        // clears the field in the preview card must still be able to send.
        subject = blankToEmpty(subject);
        body = requireText(body, "body");
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static String blankToEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
