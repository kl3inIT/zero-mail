package com.zeromail.core.chat.domain.sendaction;

import com.zeromail.core.shared.privacy.Sensitive;

@SuppressWarnings("unused")
public record ReplyEmailToolArgs(String sourceMessageId, String to, Sensitive<String> body) {

    public ReplyEmailToolArgs {
        sourceMessageId = requireText(sourceMessageId, "sourceMessageId");
        to = requireText(to, "to");
        body = requireBody(body);
    }

    public ReplyEmailToolArgs(String sourceMessageId, String to, String body) {
        this(sourceMessageId, to, Sensitive.of(requireText(body, "body")));
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
