package com.zeromail.core.waitlist.persistence;

import com.zeromail.core.shared.persistence.AbstractEntity;
import com.zeromail.core.waitlist.domain.WaitlistStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Pre-launch waitlist signup. Not tenant-owned — these rows precede tenant provisioning. Extends
 * {@link AbstractEntity} (identity only) rather than the tenant-aware bases.
 *
 * <p>The {@code email} column carries a globally UNIQUE constraint enforced by Liquibase changeset
 * 086; the service treats {@link org.springframework.dao.DataIntegrityViolationException} on INSERT
 * as a race-condition signal that the email was registered concurrently and maps it to {@link
 * com.zeromail.core.waitlist.domain.WaitlistSubscribeResult#ALREADY_REGISTERED}.
 */
@Entity
@Table(name = "waitlist_email")
public class WaitlistEmailEntity extends AbstractEntity {

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistStatus status = WaitlistStatus.PENDING;

    @Column private String source;

    @Column(name = "ip_hash")
    private String ipHash;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by_admin_id")
    private UUID approvedByAdminId;

    @Column(name = "invite_sent_at")
    private Instant inviteSentAt;

    @Column(name = "invite_next_attempt_at")
    private Instant inviteNextAttemptAt;

    @Column(name = "invite_failure_reason")
    private String inviteFailureReason;

    protected WaitlistEmailEntity() {}

    public WaitlistEmailEntity(
            UUID id, String email, String source, String ipHash, String userAgent) {
        super(id);
        this.email = email;
        this.source = source;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = WaitlistStatus.PENDING;
        }
    }

    public String getEmail() {
        return email;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public String getSource() {
        return source;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public UUID getApprovedByAdminId() {
        return approvedByAdminId;
    }

    public Instant getInviteSentAt() {
        return inviteSentAt;
    }

    public Instant getInviteNextAttemptAt() {
        return inviteNextAttemptAt;
    }

    public String getInviteFailureReason() {
        return inviteFailureReason;
    }

    public void approve(UUID adminId, Instant now) {
        if (status != WaitlistStatus.PENDING) {
            throw new IllegalStateException("approve() requires status=PENDING but was " + status);
        }
        this.status = WaitlistStatus.APPROVED;
        this.approvedAt = now;
        this.approvedByAdminId = adminId;
        this.inviteNextAttemptAt = null;
        this.inviteFailureReason = null;
    }

    public void reject(UUID adminId, Instant now) {
        if (status != WaitlistStatus.PENDING) {
            throw new IllegalStateException("reject() requires status=PENDING but was " + status);
        }
        this.status = WaitlistStatus.REJECTED;
        this.approvedAt = now;
        this.approvedByAdminId = adminId;
    }

    public void markInvited(Instant now) {
        if (status != WaitlistStatus.APPROVED) {
            throw new IllegalStateException(
                    "markInvited() requires status=APPROVED but was " + status);
        }
        this.status = WaitlistStatus.INVITED;
        this.inviteSentAt = now;
        this.inviteNextAttemptAt = null;
        this.inviteFailureReason = null;
    }

    public void markInviteFailed(String reason, Instant now) {
        if (status != WaitlistStatus.APPROVED) {
            throw new IllegalStateException(
                    "markInviteFailed() requires status=APPROVED but was " + status);
        }
        this.status = WaitlistStatus.INVITE_FAILED;
        this.inviteSentAt = now;
        this.inviteNextAttemptAt = null;
        this.inviteFailureReason = reason;
    }

    public void scheduleInviteRetry(Instant nextAttemptAt, String transientReason) {
        if (status != WaitlistStatus.APPROVED) {
            throw new IllegalStateException(
                    "scheduleInviteRetry() requires status=APPROVED but was " + status);
        }
        this.inviteNextAttemptAt = nextAttemptAt;
        this.inviteFailureReason = transientReason;
    }
}
