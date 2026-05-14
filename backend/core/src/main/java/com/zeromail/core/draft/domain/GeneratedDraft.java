package com.zeromail.core.draft.domain;

import java.util.Objects;

public record GeneratedDraft(String draftId, String gmailThreadId, DraftStatus status) {

    public GeneratedDraft {
        draftId = requireText(draftId, "draftId");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        Objects.requireNonNull(status, "status must not be null");
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
