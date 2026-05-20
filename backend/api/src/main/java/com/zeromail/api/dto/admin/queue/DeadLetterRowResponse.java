package com.zeromail.api.dto.admin.queue;

import com.zeromail.core.admin.queue.projection.DeadLetterRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire row for the dead-letter table. Intentionally has no body-shaped accessor; AdminPathBodyBan
 * regex would reject any such field.
 */
@Schema(requiredProperties = {"jobId", "jobType", "retryCount", "adminRequeueCount", "createdAt"})
public record DeadLetterRowResponse(
        UUID jobId,
        String jobType,
        String lastFailureReason,
        int retryCount,
        int adminRequeueCount,
        Instant lastFailedAt,
        Instant createdAt) {

    public static DeadLetterRowResponse from(DeadLetterRow row) {
        return new DeadLetterRowResponse(
                row.jobId(),
                row.jobType(),
                row.lastFailureReason(),
                row.retryCount(),
                row.adminRequeueCount(),
                row.lastFailedAt(),
                row.createdAt());
    }
}
