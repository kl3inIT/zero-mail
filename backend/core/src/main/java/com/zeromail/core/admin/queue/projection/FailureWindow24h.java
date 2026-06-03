package com.zeromail.core.admin.queue.projection;

/**
 * 24h-bounded failure window: {@code failedCount} FAILED rows (numerator) over {@code sampleSize}
 * rows created in the same window (denominator). Exposed so the UI can suppress a misleading
 * percentage when the sample is too small — e.g. a single failed job in a quiet day reads as "1/1 =
 * 100%", which alarms without informing. The raw counts let the client render an honest "n/a (1/1)"
 * below a minimum-sample threshold and a real percentage above it.
 */
public record FailureWindow24h(int failedCount, int sampleSize) {

    public double rate() {
        return sampleSize == 0 ? 0.0 : (double) failedCount / sampleSize;
    }
}
