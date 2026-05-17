package com.zeromail.core.rules.usecases;

import java.util.UUID;

public record RuleCompileCommand(
        UUID tenantId,
        String sourceText,
        String clarificationAnswer,
        String priorCompileContext,
        String priorDraftJson,
        String editInstruction) {

    private static final int MAX_SOURCE_TEXT_LENGTH = 4_000;
    private static final int MAX_CLARIFICATION_ANSWER_LENGTH = 1_000;
    private static final int MAX_PRIOR_CONTEXT_LENGTH = 2_000;
    private static final int MAX_PRIOR_DRAFT_LENGTH = 4_000;
    private static final int MAX_EDIT_INSTRUCTION_LENGTH = 500;

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
        priorDraftJson =
                normalizeOptionalText(priorDraftJson, "priorDraftJson", MAX_PRIOR_DRAFT_LENGTH);
        editInstruction =
                normalizeOptionalText(
                        editInstruction, "editInstruction", MAX_EDIT_INSTRUCTION_LENGTH);
    }

    public RuleCompileCommand(UUID tenantId, String sourceText) {
        this(tenantId, sourceText, null, null, null, null);
    }

    public RuleCompileCommand(
            UUID tenantId,
            String sourceText,
            String clarificationAnswer,
            String priorCompileContext) {
        this(tenantId, sourceText, clarificationAnswer, priorCompileContext, null, null);
    }

    public boolean isRefinement() {
        return priorDraftJson != null && editInstruction != null;
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
