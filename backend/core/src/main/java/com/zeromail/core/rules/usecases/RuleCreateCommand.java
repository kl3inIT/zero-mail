package com.zeromail.core.rules.usecases;

import java.util.UUID;

public record RuleCreateCommand(
        UUID ruleId,
        UUID tenantId,
        String displayName,
        String sourceText,
        RuleCompileResult compileResult,
        String templateKey,
        Integer templateVersion) {

    private static final int MAX_DISPLAY_NAME_LENGTH = 160;
    private static final int MAX_SOURCE_TEXT_LENGTH = 4_000;

    public RuleCreateCommand {
        ruleId = ruleId == null ? UUID.randomUUID() : ruleId;
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        displayName = requireBoundedText(displayName, "displayName", MAX_DISPLAY_NAME_LENGTH);
        sourceText = requireBoundedText(sourceText, "sourceText", MAX_SOURCE_TEXT_LENGTH);
        requireCompiled(compileResult);
        templateKey = normalizeOptionalText(templateKey, "templateKey", 128);
        if (templateVersion != null && templateVersion < 1) {
            throw new IllegalArgumentException("templateVersion must be positive");
        }
    }

    public RuleCreateCommand(
            UUID tenantId, String displayName, String sourceText, RuleCompileResult compileResult) {
        this(null, tenantId, displayName, sourceText, compileResult, null, null);
    }

    static String requireBoundedText(String text, String fieldName, int maxLength) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalizedText = text.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        if (normalizedText.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalizedText;
    }

    static String normalizeOptionalText(String text, String fieldName, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalizedText = text.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        if (normalizedText.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        return normalizedText;
    }

    static void requireCompiled(RuleCompileResult compileResult) {
        if (compileResult == null || !compileResult.isCompiled()) {
            throw new IllegalArgumentException("compileResult must be compiled");
        }
    }
}
