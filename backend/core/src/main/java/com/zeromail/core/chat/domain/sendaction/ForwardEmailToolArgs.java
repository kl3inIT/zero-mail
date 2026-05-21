package com.zeromail.core.chat.domain.sendaction;

import com.zeromail.core.shared.privacy.Sensitive;

@SuppressWarnings("unused")
public record ForwardEmailToolArgs(
        String sourceMessageId, String to, Sensitive<String> additionalBody) {

    public ForwardEmailToolArgs {
        sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
        to = requireText(to, "to");
        additionalBody = requireAdditionalBody(additionalBody);
    }

    public ForwardEmailToolArgs(String sourceMessageId, String to, String additionalBody) {
        this(sourceMessageId, to, Sensitive.of(requireText(additionalBody, "additionalBody")));
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static Sensitive<String> requireAdditionalBody(Sensitive<String> additionalBody) {
        if (additionalBody == null || additionalBody.value().isBlank()) {
            throw new IllegalArgumentException("additionalBody must not be blank");
        }
        return additionalBody;
    }
}
