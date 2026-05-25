package com.zeromail.core.chat.domain.sendaction;

import com.zeromail.core.shared.privacy.Sensitive;

@SuppressWarnings("unused")
public record ReplyEmailToolArgs(
        String sourceMessageId,
        String to,
        String cc,
        String subject,
        String gmailThreadId,
        Sensitive<String> body) {

    public ReplyEmailToolArgs {
        sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
        to = requireText(to, "to");
        cc = optionalText(cc);
        subject = requireText(subject, "subject");
        gmailThreadId = optionalText(gmailThreadId);
        body = requireBody(body);
    }

    public ReplyEmailToolArgs(
            String sourceMessageId,
            String to,
            String cc,
            String subject,
            String gmailThreadId,
            String body) {
        this(
                sourceMessageId,
                to,
                cc,
                subject,
                gmailThreadId,
                Sensitive.of(requireText(body, "body")));
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

    private static Sensitive<String> requireBody(Sensitive<String> body) {
        if (body == null || body.value().isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        return body;
    }
}
