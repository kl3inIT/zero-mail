package com.zeromail.core.cleanup.persistence;

import com.zeromail.core.cleanup.domain.UnsubscribeAttemptState;
import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Per-sender attempt row inside an {@link UnsubscribeCampaignEntity} (Phase 8, table {@code
 * unsubscribe_attempt}, changelog 045). Tenancy is implicit via the campaign FK — this entity
 * intentionally does NOT extend {@link
 * com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity} because the underlying table has
 * no {@code tenant_id} column (cascade-deletes with parent campaign on tenant removal).
 *
 * <p>Extends {@link AbstractEntity} directly for the {@code UUID id} field — no inherited {@code
 * created_at}/{@code updated_at}/{@code version} block because changelog 045 does not declare those
 * columns. Lifecycle is captured by {@code started_at} + {@code finished_at}.
 */
@Entity
@Table(name = "unsubscribe_attempt")
@AttributeOverride(name = "id", column = @Column(name = "id", nullable = false))
@SuppressWarnings("JpaDataSourceORMInspection")
public class UnsubscribeAttemptEntity extends AbstractEntity {

    private static final EnumSet<UnsubscribeAttemptState> RESETTABLE_STATES =
            EnumSet.of(
                    UnsubscribeAttemptState.PENDING,
                    UnsubscribeAttemptState.RUNNING,
                    UnsubscribeAttemptState.FAILED);

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "sender_email", nullable = false, length = 320)
    private String senderEmail;

    @Column(name = "sender_domain", nullable = false, length = 255)
    private String senderDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "unsubscribe_method", nullable = false, length = 16)
    private UnsubscribeMethod unsubscribeMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private UnsubscribeAttemptState state;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "archived_message_count", nullable = false)
    private int archivedMessageCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected UnsubscribeAttemptEntity() {
        // Hibernate
    }

    public UnsubscribeAttemptEntity(
            UUID attemptId,
            UUID campaignId,
            String senderEmail,
            String senderDomain,
            UnsubscribeMethod unsubscribeMethod) {
        super(attemptId);
        this.campaignId = requireUuid(campaignId, "campaignId");
        this.senderEmail = requireText(senderEmail, "senderEmail");
        this.senderDomain = requireText(senderDomain, "senderDomain");
        this.unsubscribeMethod = requireMethod(unsubscribeMethod);
        this.state = UnsubscribeAttemptState.PENDING;
        this.archivedMessageCount = 0;
    }

    public UUID getAttemptId() {
        return getId();
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public String getSenderDomain() {
        return senderDomain;
    }

    public UnsubscribeMethod getUnsubscribeMethod() {
        return unsubscribeMethod;
    }

    public UnsubscribeAttemptState getState() {
        return state;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getArchivedMessageCount() {
        return archivedMessageCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void markRunning(Instant startedAt) {
        requireFromState(UnsubscribeAttemptState.PENDING);
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        this.state = UnsubscribeAttemptState.RUNNING;
        this.startedAt = startedAt;
    }

    public void markOk(int archivedMessageCount, Instant finishedAt) {
        requireFromState(UnsubscribeAttemptState.RUNNING);
        if (archivedMessageCount < 0) {
            throw new IllegalArgumentException("archivedMessageCount must be >= 0");
        }
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt must not be null");
        }
        this.state = UnsubscribeAttemptState.OK;
        this.archivedMessageCount = archivedMessageCount;
        this.finishedAt = finishedAt;
    }

    public void markFailed(String failureReason, Instant finishedAt) {
        requireFromState(UnsubscribeAttemptState.RUNNING);
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason must not be blank");
        }
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt must not be null");
        }
        this.state = UnsubscribeAttemptState.FAILED;
        this.failureReason = failureReason;
        this.finishedAt = finishedAt;
    }

    /**
     * Reset an attempt back to {@code PENDING}, clearing transient progress fields. Used by the
     * worker when re-queueing a throttle-deferred attempt (Plan 06) and by {@code
     * CampaignRetryService} when retrying a {@code FAILED} attempt (Plan 07).
     *
     * <p>Allowed from-states: {@code PENDING}, {@code RUNNING}, {@code FAILED}. Retrying a terminal
     * {@code OK} attempt raises {@link
     * com.zeromail.core.cleanup.exception.CampaignRetryConflictException} at the service layer —
     * this method enforces the same invariant at the entity layer.
     */
    public void resetToPending() {
        if (!RESETTABLE_STATES.contains(this.state)) {
            throw new NoSuchElementException(
                    "Cannot reset attempt in terminal state " + this.state + " to PENDING");
        }
        this.state = UnsubscribeAttemptState.PENDING;
        this.startedAt = null;
        this.finishedAt = null;
        this.failureReason = null;
    }

    private void requireFromState(UnsubscribeAttemptState expectedFromState) {
        if (this.state != expectedFromState) {
            throw new NoSuchElementException(
                    "Illegal transition from "
                            + this.state
                            + " (expected "
                            + expectedFromState
                            + ")");
        }
    }

    private static UUID requireUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static UnsubscribeMethod requireMethod(UnsubscribeMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("unsubscribeMethod must not be null");
        }
        if (method == UnsubscribeMethod.NONE) {
            throw new IllegalArgumentException(
                    "unsubscribeMethod=NONE is an in-memory routing marker and must not be"
                            + " persisted");
        }
        return method;
    }
}
