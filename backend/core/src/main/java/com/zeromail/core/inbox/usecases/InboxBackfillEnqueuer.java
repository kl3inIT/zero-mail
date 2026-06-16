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
 * <p>Callers pass the concrete Gmail mailbox to backfill. The method checks the {@code
 * processing_job} table for an open (PENDING / PROCESSING) row of the same {@code job_type} for the
 * tenant and mailbox; if absent, it inserts a fresh row + flips {@code sync_state.status} to {@code
 * BACKFILLING}. This dedup runs in a {@code REQUIRES_NEW} transaction so a caller's outer
 * transaction does not see partial state when the dedup races with another concurrent enqueue.
 */
@Service
public class InboxBackfillEnqueuer {

    public static final String JOB_TYPE = "INBOX_PROJECTION_BACKFILL";

    private static final Logger log = LoggerFactory.getLogger(InboxBackfillEnqueuer.class);

    private final GmailInboxSyncStateRepository syncStateRepository;
    private final JdbcTemplate jdbcTemplate;

    public InboxBackfillEnqueuer(
            GmailInboxSyncStateRepository syncStateRepository, JdbcTemplate jdbcTemplate) {
        this.syncStateRepository =
                Objects.requireNonNull(syncStateRepository, "syncStateRepository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * Enqueue a backfill job for the mailbox unless one is already pending or processing. Returns
     * true if a new job was inserted, false if dedup short-circuited.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enqueueIfNotPending(UUID tenantId, UUID gmailConnectionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailConnectionId, "gmailConnectionId must not be null");

        Integer openJobCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(1) FROM processing_job
                        WHERE tenant_id = ?
                          AND gmail_connection_id = ?
                          AND job_type = ?
                          AND status IN ('PENDING', 'PROCESSING')
                        """,
                        Integer.class,
                        tenantId,
                        gmailConnectionId,
                        JOB_TYPE);
        if (openJobCount != null && openJobCount > 0) {
            log.debug(
                    "event=inbox_backfill_enqueue_skipped tenantId={} gmailConnectionId={} reason=already_open",
                    tenantId,
                    gmailConnectionId);
            return false;
        }

        // JdbcTemplate INSERT bypasses Hibernate's @TenantId resolver so callers do not need to
        // bind TenantContext.TENANT before enqueueing. processing_job is shared infrastructure; the
        // tenant_id and gmail_connection_id columns are set explicitly from the mailbox reference.
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO processing_job (id, tenant_id, gmail_connection_id, job_type,
                                            payload_json, status, attempts, next_run_at,
                                            created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING', 0, NOW(), NOW(), NOW())
                """,
                jobId,
                tenantId,
                gmailConnectionId,
                JOB_TYPE,
                backfillPayload(gmailConnectionId));
        syncStateRepository.upsertStatus(
                tenantId, gmailConnectionId, InboxSyncStatus.BACKFILLING.id());
        log.info(
                "event=inbox_backfill_enqueued tenantId={} gmailConnectionId={} jobId={}",
                tenantId,
                gmailConnectionId,
                jobId);
        return true;
    }

    private static String backfillPayload(UUID gmailConnectionId) {
        return "{\"gmailConnectionId\":\"" + gmailConnectionId + "\"}";
    }
}
