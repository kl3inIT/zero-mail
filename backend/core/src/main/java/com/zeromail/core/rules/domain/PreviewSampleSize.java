package com.zeromail.core.rules.domain;

public record PreviewSampleSize(int value) {

    public static final int DEFAULT_VALUE = 25;
    public static final int MAX_VALUE = 50;

    public PreviewSampleSize {
        if (value != 10 && value != 25 && value != 50) {
            throw new IllegalArgumentException("Preview sample size must be one of 10, 25, or 50");
        }
    }

    public static PreviewSampleSize normalize(Integer requestedSampleSize) {
        if (requestedSampleSize == null) {
            return new PreviewSampleSize(DEFAULT_VALUE);
        }
        return new PreviewSampleSize(requestedSampleSize);
    }
}
