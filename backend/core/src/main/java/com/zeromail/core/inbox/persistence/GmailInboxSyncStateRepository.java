package com.zeromail.core.inbox.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Repository for the per-mailbox inbox projection sync cursor. */
public interface GmailInboxSyncStateRepository
        extends JpaRepository<GmailInboxSyncStateEntity, GmailInboxSyncStateId> {

    /**
     * UPSERT the status field only (used when enqueuing a backfill job). Creates the row if missing
     * so callers do not need a separate existence check before flipping status to BACKFILLING /
     * ERROR.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO gmail_inbox_sync_state AS sync_state (
                        tenant_id, gmail_connection_id, status, version)
                    VALUES (:tenantId, :gmailConnectionId, :status, 0)
                    ON CONFLICT (tenant_id, gmail_connection_id) DO UPDATE SET
                        status = EXCLUDED.status,
                        version = sync_state.version + 1
                    """,
            nativeQuery = true)
    @Transactional
    int upsertStatus(
            @Param("tenantId") UUID tenantId,
            @Param("gmailConnectionId") UUID gmailConnectionId,
            @Param("status") String status);

    /**
     * Mark a successful backfill: set status IDLE, advance cursors, reset error counter. Used by
     * the backfill worker after a successful Gmail fetch + bulk upsert.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO gmail_inbox_sync_state AS sync_state (
                        tenant_id, gmail_connection_id, last_history_id, last_full_sync_at,
                        status, consecutive_errors, version)
                    VALUES (
                        :tenantId, :gmailConnectionId, :lastHistoryId, :syncedAt,
                        'IDLE', 0, 0)
                    ON CONFLICT (tenant_id, gmail_connection_id) DO UPDATE SET
                        last_history_id = EXCLUDED.last_history_id,
                        last_full_sync_at = EXCLUDED.last_full_sync_at,
                        status = 'IDLE',
                        consecutive_errors = 0,
                        last_error_at = NULL,
                        last_error_code = NULL,
                        version = sync_state.version + 1
                    """,
            nativeQuery = true)
    @Transactional
    int recordBackfillSuccess(
            @Param("tenantId") UUID tenantId,
            @Param("gmailConnectionId") UUID gmailConnectionId,
            @Param("lastHistoryId") Long lastHistoryId,
            @Param("syncedAt") Instant syncedAt);

    /** Mark a terminal backfill failure for observability. Worker decides retry policy. */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO gmail_inbox_sync_state AS sync_state (
                        tenant_id, gmail_connection_id, status, consecutive_errors,
                        last_error_at, last_error_code, version)
                    VALUES (
                        :tenantId, :gmailConnectionId, 'ERROR', 1,
                        :failedAt, :errorCode, 0)
                    ON CONFLICT (tenant_id, gmail_connection_id) DO UPDATE SET
                        status = 'ERROR',
                        consecutive_errors = sync_state.consecutive_errors + 1,
                        last_error_at = EXCLUDED.last_error_at,
                        last_error_code = EXCLUDED.last_error_code,
                        version = sync_state.version + 1
                    """,
            nativeQuery = true)
    @Transactional
    int recordBackfillFailure(
            @Param("tenantId") UUID tenantId,
            @Param("gmailConnectionId") UUID gmailConnectionId,
            @Param("failedAt") Instant failedAt,
            @Param("errorCode") String errorCode);
}
