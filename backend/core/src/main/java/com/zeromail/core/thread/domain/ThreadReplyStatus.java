package com.zeromail.core.thread.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ThreadReplyStatus(
        UUID tenantId,
        String gmailThreadId,
        ThreadReplyBucket bucket,
        String lastClassifiedMessageId,
        Instant lastClassifiedAt,
        boolean hasDraft,
        String draftId,
        boolean resolved) {

    public ThreadReplyStatus {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        Objects.requireNonNull(bucket, "bucket must not be null");
        lastClassifiedMessageId = requireText(lastClassifiedMessageId, "lastClassifiedMessageId");
        Objects.requireNonNull(lastClassifiedAt, "lastClassifiedAt must not be null");
        if (draftId != null && draftId.isBlank()) {
            throw new IllegalArgumentException("draftId must not be blank");
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
}
