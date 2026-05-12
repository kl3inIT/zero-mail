package com.zeromail.core.shared.privacy;

import org.jspecify.annotations.NonNull;

public record Sensitive<T>(T value) {

    public Sensitive {
        if (value == null) {
            throw new IllegalArgumentException("Sensitive value must not be null");
        }
    }

    @Override
    public @NonNull String toString() {
        return "***REDACTED***";
    }

    public static <T> Sensitive<T> of(T value) {
        return new Sensitive<>(value);
    }
}
