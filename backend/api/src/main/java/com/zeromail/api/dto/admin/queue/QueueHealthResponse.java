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
            "deadLetterCount",
            "adminRequeuedLast24h",
            "snapshotAt"
        })
public record QueueHealthResponse(
        List<QueueDepthByTypeResponse> depthByType,
        long oldestUnleasedJobAgeSeconds,
        List<RetryDistributionBucketResponse> retryHistogram,
        double failureRateLast24h,
        int deadLetterCount,
        int adminRequeuedLast24h,
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
                snapshot.deadLetterCount(),
                snapshot.adminRequeuedLast24h(),
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
