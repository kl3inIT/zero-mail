package com.zeromail.core.draft.usecases;

import java.util.Objects;
import java.util.UUID;

public record GenerateThreadDraftCommand(UUID tenantId, String gmailThreadId) {

    public GenerateThreadDraftCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        gmailThreadId = requireText(gmailThreadId, "gmailThreadId");
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
