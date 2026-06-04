package com.zeromail.core.admin.queue.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata-only detail of a single {@code processing_job} row for the admin job detail panel.
 *
 * <p><b>Privacy invariant:</b> exposes lifecycle timestamps, attempt/retry counters and the enum-id
 * failure reason — but NEVER {@code payload_json}. Adding a body/payload accessor would trip {@code
 * AdminPathBodyBanTest}; the SQL behind it is gated by {@code QueueHealthQueryServiceSqlSpyTest}.
 */
public record JobDetail(
        UUID jobId,
        String source,
        String jobType,
        String status,
        boolean scheduled,
        int attempts,
        Instant nextRunAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant heartbeatAt,
        Instant completedAt,
        String lastFailureReason,
        int adminRequeueCount,
        Instant lastRequeuedAt) {}
