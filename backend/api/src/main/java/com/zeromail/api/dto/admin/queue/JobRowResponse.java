package com.zeromail.api.dto.admin.queue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.queue.projection.JobRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One row in the unified admin job list. Metadata only — no payload (privacy invariant
 * OPS-QUEUE-01/02). {@code scheduled} marks a future-dated PENDING row (Scheduled view-state).
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
            "createdAt"
        })
public record JobRowResponse(
        UUID jobId,
        @Schema(
                        description = "Subsystem owning the job",
                        allowableValues = {"cleanup", "catalog-sync"})
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
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant updatedAt) {

    public static JobRowResponse from(JobRow row) {
        return new JobRowResponse(
                row.jobId(),
                row.source(),
                row.jobType(),
                row.status(),
                row.scheduled(),
                row.attempts(),
                row.nextRunAt(),
                row.createdAt(),
                row.updatedAt());
    }
}
