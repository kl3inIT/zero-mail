package com.zeromail.core.thread.usecases;

import java.util.Objects;
import java.util.UUID;

public record ThreadReplyClassificationInput(
        UUID tenantId,
        String gmailThreadId,
        String lastMessageId,
        boolean lastMessageFromIsTenant,
        boolean threadHasSentLabel,
        boolean hasZeroMailDraft,
        String zeroMailDraftId,
        boolean lastMessageIsAutoReply) {

    public ThreadReplyClassificationInput {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        lastMessageId = requireText(lastMessageId, "lastMessageId");
        zeroMailDraftId = nullIfBlank(zeroMailDraftId);
        if (hasZeroMailDraft && zeroMailDraftId == null) {
            throw new IllegalArgumentException("zeroMailDraftId must not be blank");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
