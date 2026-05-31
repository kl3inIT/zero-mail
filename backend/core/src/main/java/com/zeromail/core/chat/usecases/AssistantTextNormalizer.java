package com.zeromail.core.chat.usecases;

final class AssistantTextNormalizer {

    private AssistantTextNormalizer() {}

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
}
