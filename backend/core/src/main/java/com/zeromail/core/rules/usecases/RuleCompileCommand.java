package com.zeromail.core.rules.usecases;

import java.util.UUID;

public record RuleCompileCommand(
        UUID tenantId, String sourceText, String clarificationAnswer, String priorCompileContext) {

    private static final int MAX_SOURCE_TEXT_LENGTH = 4_000;
    private static final int MAX_CLARIFICATION_ANSWER_LENGTH = 1_000;
    private static final int MAX_PRIOR_CONTEXT_LENGTH = 2_000;

    public RuleCompileCommand {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        sourceText = requireBoundedText(sourceText, "sourceText", MAX_SOURCE_TEXT_LENGTH);
        clarificationAnswer =
                normalizeOptionalText(
                        clarificationAnswer,
                        "clarificationAnswer",
                        MAX_CLARIFICATION_ANSWER_LENGTH);
        priorCompileContext =
                normalizeOptionalText(
                        priorCompileContext, "priorCompileContext", MAX_PRIOR_CONTEXT_LENGTH);
    }

    public RuleCompileCommand(UUID tenantId, String sourceText) {
        this(tenantId, sourceText, null, null);
    }

    private static String requireBoundedText(String text, String fieldName, int maxLength) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalizedText = normalizeControlCharacters(text).trim();
        if (normalizedText.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalizedText;
    }

    private static String normalizeOptionalText(String text, String fieldName, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalizedText = normalizeControlCharacters(text).trim();
        if (normalizedText.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalizedText;
    }

    private static String normalizeControlCharacters(String text) {
        return text.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ");
    }
}
