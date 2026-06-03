package com.zeromail.api.dto.admin.queue;

import com.zeromail.core.admin.queue.projection.QueueDepthByType;
import com.zeromail.core.admin.queue.projection.QueueHealthSnapshot;
import com.zeromail.core.admin.queue.projection.RetryDistributionBucket;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
        requiredProperties = {
            "depthByType",
            "oldestUnleasedJobAgeSeconds",
            "retryHistogram",
            "failureRateLast24h",
            "failedCountLast24h",
            "sampleSizeLast24h",
            "deadLetterCount",
            "snapshotAt"
        })
public record QueueHealthResponse(
        List<QueueDepthByTypeResponse> depthByType,
        long oldestUnleasedJobAgeSeconds,
        List<RetryDistributionBucketResponse> retryHistogram,
        double failureRateLast24h,
        @Schema(
                        description =
                                "FAILED rows in the last 24h (numerator of failureRateLast24h)."
                                        + " Pair with sampleSizeLast24h to render an honest count when"
                                        + " the sample is too small for a meaningful percentage.")
                int failedCountLast24h,
        @Schema(
                        description =
                                "Rows created in the last 24h (denominator of failureRateLast24h)."
                                        + " When small (e.g. < 10) the client should show counts, not a"
                                        + " percentage — 1/1 = 100% alarms without informing.")
                int sampleSizeLast24h,
        int deadLetterCount,
        Instant snapshotAt) {

    public QueueHealthResponse {
        depthByType = List.copyOf(depthByType);
        retryHistogram = List.copyOf(retryHistogram);
    }

    public static QueueHealthResponse from(QueueHealthSnapshot snapshot) {
        return new QueueHealthResponse(
                snapshot.depthByType().stream().map(QueueDepthByTypeResponse::from).toList(),
                snapshot.oldestUnleasedJobAge().toSeconds(),
                snapshot.retryHistogram().stream()
                        .map(RetryDistributionBucketResponse::from)
                        .toList(),
                snapshot.failureRateLast24h(),
                snapshot.failedCountLast24h(),
                snapshot.sampleSizeLast24h(),
                snapshot.deadLetterCount(),
                snapshot.snapshotAt());
    }

    @Schema(requiredProperties = {"jobType", "pendingCount", "processingCount"})
    public record QueueDepthByTypeResponse(String jobType, int pendingCount, int processingCount) {

        public static QueueDepthByTypeResponse from(QueueDepthByType row) {
            return new QueueDepthByTypeResponse(
                    row.jobType(), row.pendingCount(), row.processingCount());
        }
    }

    @Schema(requiredProperties = {"attemptsBucket", "rowCount"})
    public record RetryDistributionBucketResponse(int attemptsBucket, int rowCount) {

        public static RetryDistributionBucketResponse from(RetryDistributionBucket row) {
            return new RetryDistributionBucketResponse(row.attemptsBucket(), row.rowCount());
        }
    }
}
