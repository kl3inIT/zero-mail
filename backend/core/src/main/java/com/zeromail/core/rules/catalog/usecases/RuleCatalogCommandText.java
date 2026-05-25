package com.zeromail.core.rules.catalog.usecases;

final class RuleCatalogCommandText {

    private RuleCatalogCommandText() {}

    static String requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " must not be blank");
        }
        return value.trim();
    }

    static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static void requireNonNegativeDisplayOrder(int displayOrder, String parameterName) {
        if (displayOrder < 0) {
            throw new IllegalArgumentException(parameterName + " must not be negative");
        }
    }
}
