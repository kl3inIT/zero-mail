package com.zeromail.core.chat.usecases.tools;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WriteToolArguments {

    private WriteToolArguments() {}

    static String text(Map<String, Object> inputJson, String fieldName) {
        Object value = inputJson == null ? null : inputJson.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    static String optionalText(Map<String, Object> inputJson, String fieldName) {
        Object value = inputJson == null ? null : inputJson.get(fieldName);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    static UUID uuid(Map<String, Object> inputJson, String fieldName) {
        return UUID.fromString(text(inputJson, fieldName));
    }

    static int integer(Map<String, Object> inputJson, String fieldName, int defaultValue) {
        Object value = inputJson == null ? null : inputJson.get(fieldName);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    static List<String> textList(Map<String, Object> inputJson, String fieldName) {
        Object value = inputJson == null ? null : inputJson.get(fieldName);
        if (value instanceof List<?> values) {
            return values.stream().map(String::valueOf).filter(text -> !text.isBlank()).toList();
        }
        String singleValue = text(inputJson, fieldName);
        return List.of(singleValue);
    }
}
