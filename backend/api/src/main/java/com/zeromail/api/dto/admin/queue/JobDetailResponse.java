package com.zeromail.api.dto.admin.queue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.queue.projection.JobDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Metadata-only detail of a single job for the admin detail panel. Never carries {@code
 * payload_json} (privacy invariant OPS-QUEUE-01/02 + {@code AdminPathBodyBanTest}).
 */
@Schema(
        requiredProperties = {
            "jobId",
            "source",
            "jobType",
            "status",
            "scheduled",
            "attempts",
            "nextRunAt",
            "createdAt",
            "adminRequeueCount"
        })
public record JobDetailResponse(
        UUID jobId,
        String source,
        String jobType,
        @Schema(
                        allowableValues = {
                            "PENDING",
                            "PROCESSING",
                            "COMPLETED",
                            "FAILED",
                            "DEAD_LETTER",
                            "CANCELLED"
                        })
                String status,
        boolean scheduled,
        int attempts,
        Instant nextRunAt,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant startedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant heartbeatAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant completedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastFailureReason,
        int adminRequeueCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant lastRequeuedAt) {

    public static JobDetailResponse from(JobDetail detail) {
        return new JobDetailResponse(
                detail.jobId(),
                detail.source(),
                detail.jobType(),
                detail.status(),
                detail.scheduled(),
                detail.attempts(),
                detail.nextRunAt(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.startedAt(),
                detail.heartbeatAt(),
                detail.completedAt(),
                detail.lastFailureReason(),
                detail.adminRequeueCount(),
                detail.lastRequeuedAt());
    }
}
