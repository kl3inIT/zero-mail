package com.zeromail.core.admin.queue.projection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate-only snapshot of worker queue health. Carries only counts and rates — no per-row job
 * ids and never a payload reference.
 *
 * <p>{@code failureRateLast24h} is the 24h-bounded ratio of FAILED rows to rows created in the same
 * window (R-8E-H2); it asymptotes correctly to zero on quiet days instead of lifetime-averaging.
 */
public record QueueHealthSnapshot(
        List<QueueDepthByType> depthByType,
        Duration oldestUnleasedJobAge,
        List<RetryDistributionBucket> retryHistogram,
        double failureRateLast24h,
        int failedCountLast24h,
        int sampleSizeLast24h,
        int deadLetterCount,
        Instant snapshotAt) {

    public QueueHealthSnapshot {
        Objects.requireNonNull(depthByType, "depthByType must not be null");
        Objects.requireNonNull(retryHistogram, "retryHistogram must not be null");
        Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");
        depthByType = List.copyOf(depthByType);
        retryHistogram = List.copyOf(retryHistogram);
    }
}
