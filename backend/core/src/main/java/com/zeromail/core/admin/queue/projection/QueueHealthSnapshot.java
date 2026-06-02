package com.zeromail.core.admin.queue.projection;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate-only snapshot of worker queue health. Never carries per-row job ids beyond the
 * dead-letter list (which lives in {@link DeadLetterPage}), and never carries a payload reference.
 *
 * <p>{@code failureRateLast24h} is the 24h-bounded ratio of FAILED rows to rows created in the same
 * window (R-8E-H2); it asymptotes correctly to zero on quiet days instead of lifetime-averaging.
 *
 * <p>{@code adminRequeuedLast24h} surfaces repeat-offender jobs that an admin had to re-queue
 * manually (R-8E-H1).
 */
public record QueueHealthSnapshot(
        List<QueueDepthByType> depthByType,
        Duration oldestUnleasedJobAge,
        List<RetryDistributionBucket> retryHistogram,
        double failureRateLast24h,
        int failedCountLast24h,
        int sampleSizeLast24h,
        int deadLetterCount,
        int adminRequeuedLast24h,
        Instant snapshotAt) {

    public QueueHealthSnapshot {
        Objects.requireNonNull(depthByType, "depthByType must not be null");
        Objects.requireNonNull(retryHistogram, "retryHistogram must not be null");
        Objects.requireNonNull(snapshotAt, "snapshotAt must not be null");
        depthByType = List.copyOf(depthByType);
        retryHistogram = List.copyOf(retryHistogram);
    }
}
