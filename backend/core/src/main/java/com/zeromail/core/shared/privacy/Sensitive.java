package com.zeromail.core.shared.privacy;

public record Sensitive<T>(T value) {

    public Sensitive {
        if (value == null) {
            throw new IllegalArgumentException("Sensitive value must not be null");
        }
    }

    @Override
    public String toString() {
        return "***REDACTED***";
    }

    public static <T> Sensitive<T> of(T value) {
        return new Sensitive<>(value);
    }
}
