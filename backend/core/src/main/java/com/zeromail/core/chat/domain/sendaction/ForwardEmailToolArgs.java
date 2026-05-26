package com.zeromail.core.chat.domain.sendaction;

@SuppressWarnings("unused")
public record ForwardEmailToolArgs(
        String sourceMessageId,
        String to,
        String cc,
        String subject,
        String gmailThreadId,
        String additionalBody) {

    public ForwardEmailToolArgs {
        sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
        to = requireText(to, "to");
        cc = optionalText(cc);
        subject = requireText(subject, "subject");
        gmailThreadId = optionalText(gmailThreadId);
        additionalBody = requireText(additionalBody, "additionalBody");
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static String optionalText(String text) {
        if (text == null) {
            return null;
        }
        String trimmedText = text.trim();
        return trimmedText.isBlank() ? null : trimmedText;
    }
}
