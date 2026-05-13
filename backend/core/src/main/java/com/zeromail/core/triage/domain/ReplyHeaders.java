package com.zeromail.core.triage.domain;

import java.util.Objects;

public record ReplyHeaders(
        String inboundMessageId,
        String priorReferences,
        String inboundSubject,
        String replyToAddress,
        String gmailThreadId) {

    public ReplyHeaders {
        replyToAddress = requireText(replyToAddress, "replyToAddress");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        inboundMessageId = trimNullable(inboundMessageId);
        priorReferences = trimNullable(priorReferences);
        inboundSubject = Objects.requireNonNullElse(inboundSubject, "").trim();
    }

    public static ReplyHeaders of(
            String inboundMessageId,
            String priorReferences,
            String inboundSubject,
            String replyToAddress,
            String gmailThreadId) {
        return new ReplyHeaders(
                inboundMessageId, priorReferences, inboundSubject, replyToAddress, gmailThreadId);
    }

    public boolean hasMessageId() {
        return inboundMessageId != null && !inboundMessageId.isBlank();
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private static String trimNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isBlank() ? null : trimmedValue;
    }
}
