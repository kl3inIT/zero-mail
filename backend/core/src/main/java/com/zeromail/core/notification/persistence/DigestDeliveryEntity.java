package com.zeromail.core.notification.persistence;

import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.domain.DigestDeliveryStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "digest_delivery",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_digest_delivery_tenant_day",
                        columnNames = {"tenant_id", "digest_day_local"}))
public class DigestDeliveryEntity extends AbstractTenantOwnedEntity {

    @Column(name = "digest_day_local", nullable = false)
    private LocalDate digestDayLocal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DigestDeliveryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private ChannelType channel;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "external_ref", length = 255)
    private String externalRef;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected DigestDeliveryEntity() {}

    public DigestDeliveryEntity(
            UUID id,
            UUID tenantId,
            LocalDate digestDayLocal,
            DigestDeliveryStatus status,
            ChannelType channel,
            int attemptCount) {
        super(id, tenantId);
        this.digestDayLocal = digestDayLocal;
        this.status = status;
        this.channel = channel;
        this.attemptCount = attemptCount;
    }

    public LocalDate getDigestDayLocal() {
        return digestDayLocal;
    }

    public DigestDeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DigestDeliveryStatus status) {
        this.status = status;
    }

    public ChannelType getChannel() {
        return channel;
    }

    public void setChannel(ChannelType channel) {
        this.channel = channel;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
