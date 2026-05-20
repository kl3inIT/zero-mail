package com.zeromail.core.admin.queue.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * Single dead-letter row visible to the admin queue dashboard.
 *
 * <p><b>Privacy invariant (SPEC OPS-QUEUE-02 + T-08-45):</b> this record explicitly excludes any
 * field that would expose the job's stored body content. The DTO contract IS the gate — adding a
 * body accessor would trip {@link com.zeromail.core.admin.arch.AdminPathBodyBanTest} via the
 * forbidden field-name regex.
 */
public record DeadLetterRow(
        UUID jobId,
        String jobType,
        String lastFailureReason,
        int retryCount,
        int adminRequeueCount,
        Instant lastFailedAt,
        Instant createdAt) {}
