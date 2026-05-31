package com.zeromail.core.llm.byok;

public record ByokSaveCommand(String provider, String baseUrl, String apiKey) {

    public ByokSaveCommand {
        provider = requireText(provider, "provider");
        baseUrl = requireText(baseUrl, "baseUrl");
        apiKey = requireText(apiKey, "apiKey");
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text.trim();
    }
}
