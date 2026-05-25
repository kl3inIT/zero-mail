package com.zeromail.core.cleanup.persistence;

import com.zeromail.core.cleanup.domain.CampaignStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Campaign aggregate root for a bulk unsubscribe run (Phase 8, table {@code unsubscribe_campaign},
 * changelog 044). One row per user-triggered campaign holds counts and lifecycle timestamps.
 *
 * <p>{@code job_id} is nullable because D-04 transactional INSERT order is campaign → attempts →
 * job (job inserted last so the worker can never pick up before child rows exist). Once enqueued,
 * {@link #linkJob(UUID)} sets {@code job_id} and the field remains stable.
 */
@Entity
@Table(name = "unsubscribe_campaign")
@AttributeOverride(name = "id", column = @Column(name = "id", nullable = false))
@SuppressWarnings("JpaDataSourceORMInspection")
public class UnsubscribeCampaignEntity extends AbstractTenantOwnedEntity {

    @Column(name = "job_id")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CampaignStatus status;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "reverted_at")
    private Instant revertedAt;

    @Column(name = "total_sender_count", nullable = false)
    private int totalSenderCount;

    @Column(name = "total_history_message_count", nullable = false)
    private int totalHistoryMessageCount;

    protected UnsubscribeCampaignEntity() {
        // Hibernate
    }

    public UnsubscribeCampaignEntity(
            UUID campaignId, UUID tenantId, int totalSenderCount, int totalHistoryMessageCount) {
        super(campaignId, tenantId);
        if (totalSenderCount < 0) {
            throw new IllegalArgumentException("totalSenderCount must be >= 0");
        }
        if (totalHistoryMessageCount < 0) {
            throw new IllegalArgumentException("totalHistoryMessageCount must be >= 0");
        }
        this.status = CampaignStatus.QUEUED;
        this.totalSenderCount = totalSenderCount;
        this.totalHistoryMessageCount = totalHistoryMessageCount;
    }

    public UUID getCampaignId() {
        return getId();
    }

    public UUID getJobId() {
        return jobId;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getRevertedAt() {
        return revertedAt;
    }

    public int getTotalSenderCount() {
        return totalSenderCount;
    }

    public int getTotalHistoryMessageCount() {
        return totalHistoryMessageCount;
    }

    /** One-shot link of the campaign to its {@link ProcessingJobEntity} row. */
    public void linkJob(UUID jobId) {
        if (this.jobId != null) {
            throw new IllegalStateException("jobId already set");
        }
        if (jobId == null) {
            throw new IllegalArgumentException("jobId must not be null");
        }
        this.jobId = jobId;
    }

    public void markRunning() {
        requireFromState(CampaignStatus.QUEUED);
        this.status = CampaignStatus.RUNNING;
    }

    public void markCompleted(Instant appliedAt) {
        requireFromState(CampaignStatus.RUNNING);
        if (appliedAt == null) {
            throw new IllegalArgumentException("appliedAt must not be null");
        }
        this.status = CampaignStatus.COMPLETED;
        this.appliedAt = appliedAt;
    }

    public void markFailed() {
        if (this.status == CampaignStatus.COMPLETED) {
            throw new IllegalStateException("Cannot fail a completed campaign");
        }
        this.status = CampaignStatus.FAILED;
    }

    public void markReverted(Instant revertedAt) {
        if (this.appliedAt == null) {
            throw new IllegalStateException("Cannot revert a campaign that was never applied");
        }
        if (revertedAt == null) {
            throw new IllegalArgumentException("revertedAt must not be null");
        }
        this.revertedAt = revertedAt;
    }

    private void requireFromState(CampaignStatus expectedFromState) {
        if (this.status != expectedFromState) {
            throw new NoSuchElementException(
                    "Illegal transition from "
                            + this.status
                            + " (expected "
                            + expectedFromState
                            + ")");
        }
    }
}
