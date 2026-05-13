package com.zeromail.core.draft.usecases;

import com.zeromail.core.draft.domain.DraftStatus;
import java.util.Objects;

public record GenerateThreadDraftResult(
        String draftId, String gmailThreadId, DraftStatus status, String openInGmailUrl) {

    public GenerateThreadDraftResult {
        draftId = requireText(draftId, "draftId");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
        Objects.requireNonNull(status, "status must not be null");
        openInGmailUrl = requireText(openInGmailUrl, "openInGmailUrl");
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
