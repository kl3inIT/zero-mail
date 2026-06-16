package com.zeromail.core.inbox.persistence;

import com.zeromail.core.inbox.domain.InboxSyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Per-tenant cursor for the inbox projection. {@link #status} is informational write-through; the
 * authoritative answer for "is a backfill currently queued / running" is the {@code processing_job}
 * table (job_type = INBOX_PROJECTION_BACKFILL, idempotency_key = tenantId).
 */
@Entity
@Table(name = "gmail_inbox_sync_state")
@IdClass(GmailInboxSyncStateId.class)
@SuppressWarnings({"JpaDataSourceORMInspection", "unused"})
public class GmailInboxSyncStateEntity {

    @Id
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "gmail_connection_id", nullable = false)
    private UUID gmailConnectionId;

    @Column(name = "last_history_id")
    private Long lastHistoryId;

    @Column(name = "last_full_sync_at")
    private Instant lastFullSyncAt;

    @Column(name = "last_incremental_at")
    private Instant lastIncrementalAt;

    @Column(name = "status", nullable = false, length = 24)
    private String status = InboxSyncStatus.IDLE.id();

    @Column(name = "consecutive_errors", nullable = false)
    private int consecutiveErrors;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected GmailInboxSyncStateEntity() {
        // Hibernate
    }

    public GmailInboxSyncStateEntity(
            UUID tenantId, UUID gmailConnectionId, InboxSyncStatus status) {
        this.tenantId = tenantId;
        this.gmailConnectionId = gmailConnectionId;
        this.status = status.id();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getGmailConnectionId() {
        return gmailConnectionId;
    }

    public Long getLastHistoryId() {
        return lastHistoryId;
    }

    public Instant getLastFullSyncAt() {
        return lastFullSyncAt;
    }

    public Instant getLastIncrementalAt() {
        return lastIncrementalAt;
    }

    public String getStatusId() {
        return status;
    }

    public InboxSyncStatus getStatus() {
        return InboxSyncStatus.fromId(status);
    }

    public int getConsecutiveErrors() {
        return consecutiveErrors;
    }

    public Instant getLastErrorAt() {
        return lastErrorAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public int getVersion() {
        return version;
    }
}
