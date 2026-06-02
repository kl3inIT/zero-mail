package com.zeromail.core.admin.queue.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * Single row in the unified admin job list across every {@code processing_job} job type
 * (UNSUBSCRIBE_CAMPAIGN cleanup jobs + CATALOG_SYNC operator jobs share this one table).
 *
 * <p><b>Privacy invariant (SPEC OPS-QUEUE-01/02 + T-08-45):</b> like {@link DeadLetterRow}, this
 * record deliberately omits any payload/body field. The DTO contract IS the gate — {@code
 * QueueHealthQueryServiceSqlSpyTest} additionally asserts the emitted SQL never selects {@code
 * payload_json}, and {@code AdminPathBodyBanTest} rejects forbidden field names.
 *
 * <p>{@code scheduled} is a derived view-state: a PENDING row whose {@code nextRunAt} is in the
 * future is "Scheduled" (mirrors Sidekiq/Hangfire's Scheduled state), not actively waiting.
 */
public record JobRow(
        UUID jobId,
        String source,
        String jobType,
        String status,
        boolean scheduled,
        int attempts,
        Instant nextRunAt,
        Instant createdAt,
        Instant updatedAt) {}
