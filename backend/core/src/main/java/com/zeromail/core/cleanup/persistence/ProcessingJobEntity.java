package com.zeromail.core.cleanup.persistence;

import com.zeromail.core.queue.domain.JobFailureReason;
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
 * Generic Postgres-backed worker outbox row. The {@code processing_job} table is shared across
 * tenant-scoped jobs (Phase 8 cleanup unsubscribe campaigns) and operator-triggered system jobs
 * (admin catalog sync, owned by the queue / admin subsystem). Column names follow the upstream
 * schema established in Liquibase 068 (createTable, owner 8D) and 078 (DLQ extensions, owner 8E);
 * Phase 8 only adds {@code tenant_id}, {@code next_run_at}, {@code heartbeat_at}, {@code
 * started_at}, and {@code version} via Liquibase 081.
 *
 * <p><b>Status vocabulary</b> matches main's existing CHECK constraint ({@code
 * ck_processing_job_status}): {@code PENDING / PROCESSING / COMPLETED / FAILED / DEAD_LETTER}.
 * Phase 8 worker code never writes {@code QUEUED} or {@code RUNNING} directly — those concepts map
 * to {@code PENDING} and {@code PROCESSING} respectively.
 *
 * <p><b>Failure reason</b> is written as a {@link JobFailureReason} enum id into {@code
 * last_failure_reason} (VARCHAR 100, CHECK constraint enforced in DB). Free-form exception text
 * MUST NOT land in the column — the ArchUnit gate {@code WorkerFailureReasonEnumOnlyTest} enforces
 * this at compile time.
 *
 * <p><b>Multi-tenancy</b>: {@code tenant_id} is nullable at the DB layer because main's catalog
 * sync rows are operator-triggered system jobs without a tenant. Phase 8 enforces non-null at this
 * Java layer via the inherited {@link AbstractTenantOwnedEntity} constructor — tenant-scoped job
 * types (UNSUBSCRIBE_CAMPAIGN, future SEED-009 types) instantiate through this entity; system jobs
 * use a different code path that inserts NULL tenant_id natively.
 */
@Entity
@Table(name = "processing_job")
@AttributeOverride(name = "id", column = @Column(name = "id", nullable = false))
@SuppressWarnings("JpaDataSourceORMInspection")
public class ProcessingJobEntity extends AbstractTenantOwnedEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    @Column(name = "job_type", nullable = false, length = 64)
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_failure_reason", length = 100)
    private String lastFailureReason;

    protected ProcessingJobEntity() {
        // Hibernate
    }

    public ProcessingJobEntity(UUID jobId, UUID tenantId, String jobType, String payload) {
        super(jobId, tenantId);
        this.jobType = requireText(jobType, "jobType");
        this.payload = requireText(payload, "payload");
        this.status = STATUS_PENDING;
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

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public void markProcessing(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        this.status = STATUS_PROCESSING;
        this.startedAt = startedAt;
        this.heartbeatAt = startedAt;
    }

    public void markCompleted(Instant completedAt) {
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        this.status = STATUS_COMPLETED;
        this.completedAt = completedAt;
        this.heartbeatAt = null;
    }

    public void markFailed(JobFailureReason failureReason, Instant completedAt) {
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason must not be null");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        this.status = STATUS_FAILED;
        this.lastFailureReason = failureReason.id();
        this.completedAt = completedAt;
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

    /**
     * Re-queue the job after a per-domain throttle deferral (Phase 8 D-20). Status returns to
     * {@code PENDING} and the next-run cursor advances; {@code last_failure_reason} stays NULL
     * because the deferral itself is not a terminal failure — only an exhausted deferral chain that
     * the campaign cap surfaces as {@link JobFailureReason#THROTTLE_DEFERRED}.
     */
    public void deferUntil(Instant nextRunAt) {
        if (nextRunAt == null) {
            throw new IllegalArgumentException("nextRunAt must not be null");
        }
        this.status = STATUS_PENDING;
        this.heartbeatAt = null;
        this.startedAt = null;
        this.nextRunAt = nextRunAt;
    }

    /** Reset a stale PROCESSING job back to PENDING for reaper-driven recovery (Phase 8 D-03). */
    public void resetToPending() {
        this.status = STATUS_PENDING;
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
