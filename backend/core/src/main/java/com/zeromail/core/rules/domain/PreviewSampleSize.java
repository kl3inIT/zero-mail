package com.zeromail.core.rules.domain;

public record PreviewSampleSize(int value) {

    public static final int DEFAULT_VALUE = 10;
    public static final int MAX_VALUE = 20;

    public PreviewSampleSize {
        if (value != 10 && value != 20) {
            throw new IllegalArgumentException("Preview sample size must be one of 10 or 20");
        }
    }

    public static PreviewSampleSize normalize(Integer requestedSampleSize) {
        if (requestedSampleSize == null) {
            return new PreviewSampleSize(DEFAULT_VALUE);
        }
        return new PreviewSampleSize(requestedSampleSize);
    }
}
