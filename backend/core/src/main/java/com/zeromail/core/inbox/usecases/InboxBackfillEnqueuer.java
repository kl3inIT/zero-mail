package com.zeromail.core.inbox.usecases;

import com.zeromail.core.inbox.domain.InboxSyncStatus;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent enqueue for {@link #JOB_TYPE} backfill jobs.
 *
 * <p>Both call sites (eager trigger from OAuth provisioning; lazy trigger from
 * {@code RecentInboxReadService.fetchPage}) invoke {@link #enqueueIfNotPending(UUID)}. The method
 * checks the {@code processing_job} table for an open (PENDING / PROCESSING) row of the same
 * {@code job_type} for the tenant; if absent, it inserts a fresh row + flips {@code sync_state.status}
 * to {@code BACKFILLING}. This dedup runs in a {@code REQUIRES_NEW} transaction so a caller's outer
 * transaction does not see partial state when the dedup races with another concurrent enqueue.
 */
@Service
public class InboxBackfillEnqueuer {

    public static final String JOB_TYPE = "INBOX_PROJECTION_BACKFILL";

    private static final Logger log = LoggerFactory.getLogger(InboxBackfillEnqueuer.class);

    /**
     * Empty JSON payload — the handler only needs the tenantId and job type. Kept here as a
     * constant so the SQL bind is stable and the column is never written as NULL (the table column
     * is NOT NULL).
     */
    private static final String EMPTY_PAYLOAD_JSON = "{}";

    private final GmailInboxSyncStateRepository syncStateRepository;
    private final JdbcTemplate jdbcTemplate;

    public InboxBackfillEnqueuer(
            GmailInboxSyncStateRepository syncStateRepository, JdbcTemplate jdbcTemplate) {
        this.syncStateRepository =
                Objects.requireNonNull(
                        syncStateRepository, "syncStateRepository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Enqueue a backfill job for the tenant unless one is already pending or processing. Returns
     * true if a new job was inserted, false if dedup short-circuited.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enqueueIfNotPending(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        Integer openJobCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(1) FROM processing_job
                        WHERE tenant_id = ?
                          AND job_type = ?
                          AND status IN ('PENDING', 'PROCESSING')
                        """,
                        Integer.class,
                        tenantId,
                        JOB_TYPE);
        if (openJobCount != null && openJobCount > 0) {
            log.debug(
                    "event=inbox_backfill_enqueue_skipped tenantId={} reason=already_open", tenantId);
            return false;
        }

        // JdbcTemplate INSERT bypasses Hibernate's @TenantId resolver so callers do not need to
        // bind TenantContext.TENANT before enqueueing. processing_job is shared infrastructure;
        // the tenant_id column is set explicitly from the parameter.
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO processing_job (id, tenant_id, job_type, payload_json, status,
                                            attempts, next_run_at, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, 'PENDING', 0, NOW(), NOW(), NOW())
                """,
                jobId,
                tenantId,
                JOB_TYPE,
                EMPTY_PAYLOAD_JSON);
        syncStateRepository.upsertStatus(tenantId, InboxSyncStatus.BACKFILLING.id());
        log.info("event=inbox_backfill_enqueued tenantId={} jobId={}", tenantId, jobId);
        return true;
    }
}
