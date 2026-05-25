package com.zeromail.core.chat.domain.sendaction;

import com.zeromail.core.shared.privacy.Sensitive;

@SuppressWarnings("unused")
public record ForwardEmailToolArgs(
        String sourceMessageId,
        String to,
        String cc,
        String subject,
        String gmailThreadId,
        Sensitive<String> additionalBody) {

    public ForwardEmailToolArgs {
        sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
        to = requireText(to, "to");
        cc = optionalText(cc);
        subject = requireText(subject, "subject");
        gmailThreadId = optionalText(gmailThreadId);
        additionalBody = requireAdditionalBody(additionalBody);
    }

    public ForwardEmailToolArgs(
            String sourceMessageId,
            String to,
            String cc,
            String subject,
            String gmailThreadId,
            String additionalBody) {
        this(
                sourceMessageId,
                to,
                cc,
                subject,
                gmailThreadId,
                Sensitive.of(requireText(additionalBody, "additionalBody")));
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

    private static Sensitive<String> requireAdditionalBody(Sensitive<String> additionalBody) {
        if (additionalBody == null || additionalBody.value().isBlank()) {
            throw new IllegalArgumentException("additionalBody must not be blank");
        }
        return additionalBody;
    }
}
