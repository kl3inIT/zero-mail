package com.zeromail.core.inbox.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Informational status of the inbox projection sync cursor for a tenant.
 *
 * <p>This is a write-through observability field. The authoritative answer to "is a backfill
 * currently queued or running" is the {@code processing_job} table (job_type = {@code
 * INBOX_PROJECTION_BACKFILL}, idempotency_key = tenantId). Two values for "in flight" would fork
 * the dedup logic; keep the surface here small.
 *
 * <ul>
 *   <li>{@link #IDLE} — no backfill in flight, last_full_sync_at populated.
 *   <li>{@link #BACKFILLING} — a backfill job is queued or running. Worker writes IDLE on success.
 *   <li>{@link #ERROR} — terminal failure path; consecutive_errors and last_error_* populated.
 * </ul>
 */
public enum InboxSyncStatus implements IdentifiedEnum {
    IDLE,
    BACKFILLING,
    ERROR;

    @Override
    public String id() {
        return name();
    }

    public static InboxSyncStatus fromId(String id) {
        return Stream.of(values())
                .filter(status -> status.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown InboxSyncStatus id: " + id));
    }
}
