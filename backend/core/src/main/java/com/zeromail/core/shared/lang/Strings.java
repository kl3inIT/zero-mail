package com.zeromail.core.shared.lang;

import java.util.Objects;

public final class Strings {

    private Strings() {}

    /**
     * Returns {@code value.trim()} when non-blank; throws if null or blank. Centralized so domain
     * records, query objects, and validators share one blank-string guard shape.
     */
    public static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmedValue = value.trim();
        if (trimmedValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmedValue;
    }
}
