package com.zeromail.core.rules.domain;

public record PreviewSampleSize(int value) {

    public static final int DEFAULT_VALUE = 100;
    public static final int MAX_VALUE = 100;

    public PreviewSampleSize {
        if (value != 10 && value != 20 && value != 50 && value != MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Preview sample size must be one of 10, 20, 50, or 100");
        }
    }

    public static PreviewSampleSize normalize(Integer requestedSampleSize) {
        if (requestedSampleSize == null) {
            return new PreviewSampleSize(DEFAULT_VALUE);
        }
        return new PreviewSampleSize(requestedSampleSize);
    }
}
