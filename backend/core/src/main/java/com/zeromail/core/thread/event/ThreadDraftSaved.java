package com.zeromail.core.thread.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ThreadDraftSaved(
        UUID tenantId, String gmailThreadId, String draftId, Instant observedAt) {

    public ThreadDraftSaved {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        draftId = requireText(draftId, "draftId");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
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
