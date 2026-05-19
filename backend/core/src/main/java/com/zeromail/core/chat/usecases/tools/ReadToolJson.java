package com.zeromail.core.chat.usecases.tools;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class ReadToolJson {

    private ReadToolJson() {}

    static <T> T readArgs(ObjectMapper objectMapper, String inputJson, Class<T> argsClass) {
        try {
            return objectMapper.readValue(
                    inputJson == null || inputJson.isBlank() ? "{}" : inputJson, argsClass);
        } catch (JacksonException jacksonException) {
            throw new IllegalArgumentException(
                    "chat read tool input must be valid JSON", jacksonException);
        }
    }

    static String writeOutput(ObjectMapper objectMapper, Object output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JacksonException jacksonException) {
            throw new IllegalStateException(
                    "chat read tool output could not be serialized", jacksonException);
        }
    }

    static void requireTenantMatch(String declaredTenantId, java.util.UUID boundTenantId) {
        if (declaredTenantId == null || declaredTenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!declaredTenantId.equals(boundTenantId.toString())) {
            throw new IllegalStateException("tenant context does not match requested tenant");
        }
    }

    static String cap(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalizedValue =
                value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").replaceAll("\\s+", " ").trim();
        return normalizedValue.length() <= maxLength
                ? normalizedValue
                : normalizedValue.substring(0, maxLength).trim();
    }
}
