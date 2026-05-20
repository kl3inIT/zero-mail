package com.zeromail.core.cleanup.persistence;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Generic Postgres-backed worker outbox row (D-01). Phase 8 ships exactly one {@code job_type}
 * ({@code UNSUBSCRIBE_CAMPAIGN}); later phases (SEED-009 follow-up) extend the type enum via a
 * dedicated migration without touching this entity.
 *
 * <p>{@code payload} stays as a raw JSONB string — the worker dispatch layer (Plan 04/06) parses it
 * on pickup. The entity itself is payload-agnostic so the same row shape works for every future job
 * type.
 *
 * <p>{@code status} is a String, NOT an enum field, because the dispatch layer treats it as a plain
 * four-value bucket ({@code QUEUED}/{@code RUNNING}/{@code COMPLETED}/{@code FAILED}) and never
 * round-trips through a Java type. Native SQL transitions (SKIP LOCKED pickup, heartbeat reaper)
 * operate on the column directly.
 */
@Entity
@Table(name = "processing_job")
@AttributeOverride(name = "id", column = @Column(name = "id", nullable = false))
@SuppressWarnings("JpaDataSourceORMInspection")
public class ProcessingJobEntity extends AbstractTenantOwnedEntity {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Column(name = "job_type", nullable = false, length = 64)
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected ProcessingJobEntity() {
        // Hibernate
    }

    public ProcessingJobEntity(UUID jobId, UUID tenantId, String jobType, String payload) {
        super(jobId, tenantId);
        this.jobType = requireText(jobType, "jobType");
        this.payload = requireText(payload, "payload");
        this.status = STATUS_QUEUED;
        this.attempts = 0;
        this.nextRunAt = Instant.now();
    }

    public UUID getJobId() {
        return getId();
    }

    public String getJobType() {
        return jobType;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void markRunning(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        this.status = STATUS_RUNNING;
        this.startedAt = startedAt;
        this.heartbeatAt = startedAt;
    }

    public void markCompleted(Instant finishedAt) {
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt must not be null");
        }
        this.status = STATUS_COMPLETED;
        this.finishedAt = finishedAt;
        this.heartbeatAt = null;
    }

    public void markFailed(String failureReason, Instant finishedAt) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason must not be blank");
        }
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt must not be null");
        }
        this.status = STATUS_FAILED;
        this.failureReason = failureReason;
        this.finishedAt = finishedAt;
        this.heartbeatAt = null;
    }

    public void updateHeartbeat(Instant heartbeatAt) {
        if (heartbeatAt == null) {
            throw new IllegalArgumentException("heartbeatAt must not be null");
        }
        this.heartbeatAt = heartbeatAt;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    /** Reset a stale RUNNING job back to QUEUED for reaper-driven recovery (D-03). */
    public void resetToQueued() {
        this.status = STATUS_QUEUED;
        this.heartbeatAt = null;
        this.startedAt = null;
        this.nextRunAt = Instant.now();
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }
}
