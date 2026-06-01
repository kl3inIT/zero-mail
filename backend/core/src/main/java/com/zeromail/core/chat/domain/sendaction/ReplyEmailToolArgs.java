package com.zeromail.core.chat.domain.sendaction;

@SuppressWarnings("unused")
public record ReplyEmailToolArgs(
        String sourceMessageId,
        String to,
        String cc,
        String subject,
        String gmailThreadId,
        String body) {

    public ReplyEmailToolArgs {
        sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
        to = requireText(to, "to");
        cc = optionalText(cc);
        // Blank-tolerant: Gmail renders an empty subject as "(no subject)". The model
        // normally derives a "Re:" subject from the thread, but an empty value must send.
        subject = blankToEmpty(subject);
        gmailThreadId = optionalText(gmailThreadId);
        body = requireText(body, "body");
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

    private static String blankToEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
